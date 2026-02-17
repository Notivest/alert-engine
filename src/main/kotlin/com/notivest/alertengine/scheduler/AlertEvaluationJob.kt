package com.notivest.alertengine.scheduler
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.repositories.AlertRuleRepository
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class AlertEvaluationJob(
    private val alertRuleRepository: AlertRuleRepository,
    private val properties: AlertEvaluationSchedulerProperties,
    private val groupProcessor: GroupProcessor,
    private val ruleRunner: RuleRunner,
    private val eventSink: EventSink,
    private val meterRegistry: MeterRegistry,
    private val evaluationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val logger = LoggerFactory.getLogger(AlertEvaluationJob::class.java)

    private val groupsProcessedCounter = Counter.builder("alertengine.scheduler.groups.processed")
        .description("Number of symbol/timeframe groups processed in the alert evaluation job")
        .register(meterRegistry)

    suspend fun execute(cycleId: UUID, startedAt: Instant) {
        val activeRules = withContext(Dispatchers.IO) {
            alertRuleRepository.findAllByStatus(RuleStatus.ACTIVE)
        }

        if (activeRules.isEmpty()) {
            logger.info("alert-eval-cycle-empty cycleId={} message=\"no active rules found\"", cycleId)
            return
        }

        val grouped = activeRules.groupBy { it.symbol to it.timeframe }
        groupsProcessedCounter.increment(grouped.size.toDouble())

        val concurrency = properties.maxParallelEvaluations.coerceAtLeast(1)
        val evaluatedAt = startedAt

        for ((key, rules) in grouped) {
            val (symbol, timeframe) = key
            val needsQuotes = rules.any { it.kind.requiresQuotes }
            val needsHistorical = rules.any { it.kind.requiresHistorical }

            val quoteSeries = if (needsQuotes) {
                groupProcessor.loadQuoteSeries(cycleId, symbol, timeframe)
            } else {
                null
            }

            val historicalSeries = if (needsHistorical) {
                groupProcessor.loadHistoricalSeries(cycleId, symbol, timeframe, evaluatedAt)
            } else {
                null
            }
            val context = RuleEvaluationContext(evaluatedAt = evaluatedAt, timeframe = timeframe)

            rules.asSequence()
                .mapNotNull { rule ->
                    val series = when {
                        rule.kind.requiresQuotes && rule.kind.requiresHistorical ->
                            combineSeries(rule.kind, historicalSeries, quoteSeries)
                        rule.kind.requiresQuotes -> quoteSeries
                        else -> historicalSeries
                    }

                    if (series == null) {
                        logger.warn(
                            "alert-eval-rule-skipped cycleId={} ruleId={} symbol={} timeframe={} kind={} message=\"missing price data\"",
                            cycleId,
                            rule.id,
                            rule.symbol,
                            rule.timeframe,
                            rule.kind,
                        )
                        null
                    } else {
                        rule to series
                    }
                }
                .toList()
                .asFlow()
                .flatMapMerge(concurrency = concurrency) { (rule, series) ->
                    flow {
                        emit(ruleRunner.runRule(cycleId, rule, context, series))
                    }.flowOn(evaluationDispatcher)
                }
                .collect { result ->
                    when (result) {
                        is RuleRunResult.Fired -> handleTriggered(cycleId, result.rule, result.result, evaluatedAt)
                        is RuleRunResult.NoOp -> Unit
                        is RuleRunResult.Error -> Unit
                    }
                }
        }
    }

    private suspend fun handleTriggered(
        cycleId: UUID,
        rule: AlertRule,
        evaluationResult: RuleEvaluationResult,
        evaluatedAt: Instant,
    ) {
        val triggeredAt = OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC)
        if (isDebounced(rule, triggeredAt)) {
            logger.info(
                "alert-eval-rule-debounced cycleId={} ruleId={} debounceSeconds={} lastTriggeredAt={}",
                cycleId,
                rule.id,
                rule.debounceTime?.seconds,
                rule.lastTriggeredAt,
            )
            return
        }

        when (val outcome = eventSink.persist(cycleId, rule, triggeredAt, evaluationResult)) {
            is EventSinkResult.Idempotent -> {
                // logging handled in sink
            }
            is EventSinkResult.Persisted -> {
                if (!outcome.notificationSent) {
                    logger.warn(
                        "alert-eval-rule-notification-pending cycleId={} ruleId={} fingerprint={} message=\"notification delivery pending\"",
                        cycleId,
                        rule.id,
                        evaluationResult.fingerprint,
                    )
                }
            }
            is EventSinkResult.Failure -> {
                // already logged inside sink
            }
        }
    }

    private fun isDebounced(rule: AlertRule, triggeredAt: OffsetDateTime): Boolean {
        val debounce = rule.debounceTime ?: return false
        val lastTriggered = rule.lastTriggeredAt ?: return false
        val elapsed = Duration.between(lastTriggered, triggeredAt)
        return elapsed < debounce
    }

    private fun combineSeries(
        kind: AlertKind,
        historicalSeries: DefaultPriceSeries?,
        quoteSeries: DefaultPriceSeries?,
    ): DefaultPriceSeries? {
        val historical = historicalSeries ?: return null
        val quote = quoteSeries ?: return null
        val quoteCandle = quote.candles.lastOrNull() ?: return null
        val quotePrice = quote.currentPrice ?: quoteCandle.close
        val quoteTs = quote.currentPriceAsOf ?: quoteCandle.openTime
        return when (kind) {
            AlertKind.DRAWDOWN -> {
                historical.copy(
                    currentPrice = quotePrice,
                    currentPriceAsOf = quoteTs,
                )
            }
            AlertKind.PCT_CHANGE -> {
                val mergedCandles = mergeWithQuoteCandle(historical.candles, quoteCandle)
                historical.copy(
                    candles = mergedCandles,
                    currentPrice = quotePrice,
                    currentPriceAsOf = quoteTs,
                )
            }
            else -> historical
        }
    }

    private fun mergeWithQuoteCandle(
        historicalCandles: List<Candle>,
        quoteCandle: Candle,
    ): List<Candle> {
        if (historicalCandles.isEmpty()) {
            return listOf(quoteCandle)
        }

        val lastHistorical = historicalCandles.last()
        return when {
            quoteCandle.openTime.isAfter(lastHistorical.openTime) -> historicalCandles + quoteCandle
            quoteCandle.openTime == lastHistorical.openTime -> historicalCandles.dropLast(1) + quoteCandle
            else -> (historicalCandles + quoteCandle).sortedBy { it.openTime }
        }
    }
}

private val AlertKind.requiresQuotes: Boolean
    get() = when (this) {
        AlertKind.PRICE_THRESHOLD,
        AlertKind.PCT_CHANGE,
        AlertKind.DRAWDOWN -> true
        else -> false
    }

private val AlertKind.requiresHistorical: Boolean
    get() = when (this) {
        AlertKind.PRICE_THRESHOLD -> false
        else -> true
    }
