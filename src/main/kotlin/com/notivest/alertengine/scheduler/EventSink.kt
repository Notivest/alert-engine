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
    ): EventSinkResult {
        val payload: Map<String, Any> = evaluationResult.payload?.let {
            objectMapper.convertValue(it, mapType)
        } ?: emptyMap()

        return try {
            val txResult = withContext(Dispatchers.IO) {
                transactionTemplate.execute {
                    if (alertEventRepository.existsByRuleIdAndFingerprint(rule.id, evaluationResult.fingerprint)) {
                        EventSinkResult.Idempotent
                    } else {
                        val event = AlertEvent(
                            rule = rule,
                            triggeredAt = triggeredAt,
                            payload = payload,
                            fingerprint = evaluationResult.fingerprint,
                            severity = evaluationResult.severity,
                        )
                        val saved = alertEventRepository.save(event)
                        rule.lastTriggeredAt = triggeredAt
                        alertRuleRepository.save(rule)
                        alertsTriggeredCounter.increment()
                        EventSinkResult.Persisted(saved)
                    }
                }
            } ?: EventSinkResult.Failure(IllegalStateException("transaction returned null"))

            when (txResult) {
                is EventSinkResult.Persisted -> {
                    logger.info(
                        "alert-eval-rule-triggered cycleId={} ruleId={} fingerprint={} severity={}",
                        cycleId,
                        rule.id,
                        evaluationResult.fingerprint,
                        evaluationResult.severity,
                    )

                    val notificationSent = try {
                        withContext(Dispatchers.IO) {
                            notificationService.send(txResult.event)
                        }
                        true
                    } catch (ex: Exception) {
                        logger.error(
                            "alert-eval-rule-notification-error cycleId={} ruleId={} fingerprint={} message=\"failed to emit notification\"",
                            cycleId,
                            rule.id,
                            evaluationResult.fingerprint,
                            ex,
                        )
                        errorMetrics.increment("notification")
                        false
                    }

                    if (!notificationSent) {
                        txResult.copy(notificationSent = false)
                    } else {
                        txResult
                    }
                }

                EventSinkResult.Idempotent -> {
                    logger.info(
                        "alert-eval-rule-idempotent cycleId={} ruleId={} fingerprint={} message=\"duplicate fingerprint skipped\"",
                        cycleId,
                        rule.id,
                        evaluationResult.fingerprint,
                    )
                    txResult
                }

                is EventSinkResult.Failure -> {
                    logger.error(
                        "alert-eval-rule-persist-error cycleId={} ruleId={} fingerprint={} message=\"failed to persist event\"",
                        cycleId,
                        rule.id,
                        evaluationResult.fingerprint,
                        txResult.exception,
                    )
                    errorMetrics.increment("persistence")
                    txResult
                }
            }
        } catch (ex: Exception) {
            logger.error(
                "alert-eval-rule-persist-error cycleId={} ruleId={} fingerprint={} message=\"failed to persist event\"",
                cycleId,
                rule.id,
                evaluationResult.fingerprint,
                ex,
            )
            errorMetrics.increment("persistence")
            EventSinkResult.Failure(ex)
        }
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