package com.notivest.alertengine.scheduler

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.notification.NotificationService
import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.repositories.AlertRuleRepository
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class EventSink(
    private val alertEventRepository: AlertEventRepository,
    private val alertRuleRepository: AlertRuleRepository,
    private val notificationService: NotificationService,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    private val errorMetrics: ErrorMetrics,
    transactionManager: PlatformTransactionManager,
    private val ruleStateStore: RuleStateStore,
) {

    private val logger = LoggerFactory.getLogger(EventSink::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val alertsTriggeredCounter: Counter = Counter.builder("alertengine.scheduler.alerts.triggered")
        .description("Number of alert events triggered by the scheduler")
        .register(meterRegistry)

    suspend fun persist(
        cycleId: UUID,
        rule: AlertRule,
        triggeredAt: OffsetDateTime,
        evaluationResult: RuleEvaluationResult,
    ): EventSinkResult = runCatching {
        val payload = evaluationResult.payload.toMap(objectMapper)
        val ruleId = requireNotNull(rule.id) { "rule must be persisted before persisting events" }
        val barToTs = extractBarInstant(payload)

        if (barToTs != null) {
            val lastToTs = ruleStateStore.getLastToTs(ruleId)
            if (lastToTs == barToTs) {
                logger.debug(
                    "alert-event-debounced ruleId={} fingerprint={} toTs={}",
                    ruleId,
                    evaluationResult.fingerprint,
                    barToTs,
                )
                return EventSinkResult.Idempotent
            }
        }

        val txResult = withContext(Dispatchers.IO) {
            insertEventTx(rule, triggeredAt, evaluationResult, payload, barToTs)
        }

        when (txResult) {
            is EventSinkResult.Persisted -> {
                val shouldNotify = txResult.event.severity.ordinal >= rule.notifyMinSeverity.ordinal
                if (!shouldNotify) {
                    txResult.copy(notificationSent = false)
                } else {
                    val sent = sendNotification(txResult.event)
                    if (sent) {
                        markSent(txResult.event)
                        txResult.copy(notificationSent = true)
                    } else {
                        txResult.copy(notificationSent = false)
                    }
                }
            }
            else -> txResult
        }
    }.getOrElse { ex ->
        errorMetrics.increment("persistence")
        logger.error("persist-failed fp={}", evaluationResult.fingerprint, ex)
        EventSinkResult.Failure(ex)
    }

    private fun insertEventTx(
        rule: AlertRule,
        triggeredAt: OffsetDateTime,
        result: RuleEvaluationResult,
        payload: Map<String, Any>,
        barToTs: Instant?,
    ): EventSinkResult =
        transactionTemplate.execute {
            val ruleId = requireNotNull(rule.id) { "rule must be persisted" }
            val eventId = UUID.randomUUID()
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val rowsInserted = alertEventRepository.insertIfAbsent(
                id = eventId,
                ruleId = ruleId,
                triggeredAt = triggeredAt,
                payload = objectMapper.writeValueAsString(payload),
                fingerprint = result.fingerprint,
                severity = result.severity.name,
                sent = false,
                createdAt = now,
                updatedAt = now,
            )

            if (rowsInserted == 0) {
                logger.debug(
                    "alert-event-duplicate ruleId={} fingerprint={}",
                    ruleId,
                    result.fingerprint,
                )
                EventSinkResult.Idempotent
            } else {
                val saved = alertEventRepository.findByRuleIdAndFingerprint(ruleId, result.fingerprint)
                    ?: throw IllegalStateException("inserted alert event not found")
                rule.lastTriggeredAt = triggeredAt
                alertRuleRepository.save(rule)
                ruleStateStore.updateLastToTs(ruleId, barToTs)
                alertsTriggeredCounter.increment()
                EventSinkResult.Persisted(saved)
            }
        } ?: EventSinkResult.Failure(IllegalStateException("tx returned null"))

    private fun extractBarInstant(payload: Map<String, Any>): Instant? {
        val toTs = payload["toTs"]?.toString()
        val asOf = payload["asOf"]?.toString()
        val candidate = toTs ?: asOf ?: return null
        return runCatching { Instant.parse(candidate) }.getOrNull()
    }

    private suspend fun sendNotification(event: AlertEvent): Boolean =
        runCatching { withContext(Dispatchers.IO) { notificationService.send(event) } }
            .onFailure {
                logger.error("notification-failed fp={}", event.fingerprint, it)
                errorMetrics.increment("notification")
            }
            .isSuccess

    private suspend fun markSent(event: AlertEvent) {
        withContext(Dispatchers.IO) {
            transactionTemplate.execute {
                event.sent = true
                alertEventRepository.save(event)
            }
        }
    }

    // utilidades cortas
    private fun Any?.toMap(objectMapper: ObjectMapper): Map<String, Any> =
        this?.let { objectMapper.convertValue(it, mapType) } ?: emptyMap()

    companion object {
        private val mapType = object : TypeReference<Map<String, Any>>() {}
    }

}

sealed interface EventSinkResult {
    data class Persisted(
        val event: AlertEvent,
        val notificationSent: Boolean = true,
    ) : EventSinkResult

    data object Idempotent : EventSinkResult

    data class Failure(val exception: Throwable) : EventSinkResult
}