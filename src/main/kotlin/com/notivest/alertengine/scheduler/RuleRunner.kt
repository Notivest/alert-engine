package com.notivest.alertengine.scheduler

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.ruleEvaluators.RuleEvaluationOrchestrator
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class RuleRunner(
    private val orchestrator: RuleEvaluationOrchestrator,
    private val meterRegistry: MeterRegistry,
    private val errorMetrics: ErrorMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val logger = LoggerFactory.getLogger(RuleRunner::class.java)
    private val evaluationTimer = Timer.builder("alertengine.scheduler.rule.latency")
        .description("Latency of individual rule evaluations")
        .register(meterRegistry)
    private val rulesEvaluatedCounter: Counter = Counter.builder("alertengine.scheduler.rules.evaluated")
        .description("Number of alert rules evaluated")
        .register(meterRegistry)

    suspend fun runRule(
        cycleId: UUID,
        rule: AlertRule,
        context: RuleEvaluationContext,
        series: DefaultPriceSeries,
    ): RuleRunResult {
        val timerSample = Timer.start(meterRegistry)
        val startedAt = Instant.now(clock)
        return try {
            logger.debug(
                "alert-eval-rule-start cycleId={} ruleId={} symbol={} timeframe={} kind={}",
                cycleId,
                rule.id,
                rule.symbol,
                rule.timeframe,
                rule.kind,
            )

            rulesEvaluatedCounter.increment()

            val result = orchestrator.evaluate(context, rule, series)
            logger.debug(
                "alert-eval-rule-complete cycleId={} ruleId={} triggered={} durationMs={}",
                cycleId,
                rule.id,
                result.triggered,
                Duration.between(startedAt, Instant.now(clock)).toMillis(),
            )

            if (result.triggered) {
                RuleRunResult.Fired(rule, result)
            } else {
                RuleRunResult.NoOp(rule, reason = "not_triggered")
            }
        } catch (ex: Exception) {
            logger.error(
                "alert-eval-rule-error cycleId={} ruleId={} symbol={} timeframe={} kind={} message=\"evaluation failed\"",
                cycleId,
                rule.id,
                rule.symbol,
                rule.timeframe,
                rule.kind,
                ex,
            )
            errorMetrics.increment("evaluation")
            RuleRunResult.Error(rule, ex)
        } finally {
            timerSample.stop(evaluationTimer)
        }
    }
}