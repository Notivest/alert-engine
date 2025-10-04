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
import java.time.OffsetDateTime
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

        val txResult = withContext(Dispatchers.IO) {
            insertEventTx(rule, triggeredAt, evaluationResult, payload)
        }

        when (txResult) {
            is EventSinkResult.Persisted -> {
                val sent = sendNotification(txResult.event)
                if (sent) markSent(txResult.event)
                if (!sent) txResult.copy(notificationSent = false) else txResult
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
        payload: Map<String, Any>
    ): EventSinkResult =
        transactionTemplate.execute {
            try {
                val event = AlertEvent(
                    rule = rule,
                    triggeredAt = triggeredAt,
                    payload = payload,
                    fingerprint = result.fingerprint,
                    severity = result.severity.toSeverityAlert(),
                    sent = false
                )
                val saved = alertEventRepository.save(event)
                rule.lastTriggeredAt = triggeredAt
                alertRuleRepository.save(rule)
                alertsTriggeredCounter.increment()
                EventSinkResult.Persisted(saved)
            } catch (ex: org.springframework.dao.DataIntegrityViolationException) {
                // Choque con UNIQUE(rule_id, fingerprint) => idempotente
                EventSinkResult.Idempotent
            }
        } ?: EventSinkResult.Failure(IllegalStateException("tx returned null"))

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

    private fun Any?.toSeverityAlert(): com.notivest.alertengine.models.enums.SeverityAlert =
        when (this?.toString()) {
            "CRITICAL" -> com.notivest.alertengine.models.enums.SeverityAlert.CRITICAL
            "WARNING"  -> com.notivest.alertengine.models.enums.SeverityAlert.WARNING
            else       -> com.notivest.alertengine.models.enums.SeverityAlert.INFO
        }

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