package com.notivest.alertengine.notification

import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.pricefetcher.tokenprovider.TokenPolicy
import com.notivest.alertengine.repositories.AlertRuleRepository
import kotlinx.coroutines.reactive.awaitSingle
import org.hibernate.Hibernate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.OffsetDateTime
import java.util.UUID

@Service
class NotificationServiceImpl(
    @Qualifier("notificationWebClient")
    private val client: WebClient,
    private val properties: NotificationClientProperties,
    private val alertRuleRepository: AlertRuleRepository,
    private val tokenPolicy: TokenPolicy,
) : NotificationService {

    private val logger = LoggerFactory.getLogger(NotificationServiceImpl::class.java)

    override suspend fun send(event: AlertEvent) {
        val rule = resolveRule(event)
        val token = tokenPolicy.resolveToken()
        val request = AlertNotificationRequest(
            userId = rule.userId,
            fingerprint = event.fingerprint,
            occurredAt = event.triggeredAt.toInstant().toString(),
            severity = event.severity.name,
            templateKey = resolveTemplateKey(rule),
            templateData = buildTemplateData(event, rule)
        )

        // Log before sending the notification request
        logger.debug(
            "notification-sending userId={} fingerprint={} templateKey={} severity={} path={} occurredAt={}",
            request.userId,
            request.fingerprint,
            request.templateKey,
            request.severity,
            normalizePath(properties.alertPath),
            request.occurredAt,
        )

        val response = client.post()
            .uri(normalizePath(properties.alertPath))
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers -> if (!token.isNullOrBlank()) headers.setBearerAuth(token) }
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AlertNotificationResponse::class.java)
            .awaitSingle()
            ?: throw NotificationServiceException("notification-service returned empty body")

        if (!response.accepted) {
            throw NotificationRejectedException(response.reason)
        }

        logger.debug(
            "notification-enqueued userId={} fingerprint={} jobId={} scheduledAt={}",
            rule.userId,
            event.fingerprint,
            response.jobId,
            response.scheduledAt,
        )
    }

    private fun resolveRule(event: AlertEvent): AlertRule {
        val rule = event.rule
        return if (Hibernate.isInitialized(rule)) {
            rule
        } else {
            val ruleId = requireNotNull(rule.id) { "Alert event missing associated rule id" }
            alertRuleRepository.findById(ruleId).orElseThrow {
                IllegalStateException("Alert rule $ruleId not found for event ${event.id}")
            }
        }
    }

    private fun resolveTemplateKey(rule: AlertRule): String =
        properties.alertTemplateKey.ifBlank {
            "alert-${rule.kind.name.lowercase().replace('_', '-')}"
        }

    private fun buildTemplateData(event: AlertEvent, rule: AlertRule): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "eventId" to event.id?.toString(),
            "ruleId" to rule.id.toString(),
            "ruleKind" to rule.kind.name,
            "symbol" to rule.symbol,
            "triggeredAt" to event.triggeredAt.toInstant().toString(),
            "severity" to event.severity.name,
            "fingerprint" to event.fingerprint,
            "payload" to event.payload,
        )
        if (rule.params.isNotEmpty()) {
            data["ruleParams"] = rule.params
        }
        return data
    }

    private fun normalizePath(path: String): String =
        if (path.startsWith("/")) path else "/$path"

    data class AlertNotificationRequest(
        val userId: UUID,
        val fingerprint: String,
        val occurredAt: String,
        val severity: String,
        val templateKey: String,
        val templateData: Map<String, Any?>,
    )

    data class AlertNotificationResponse(
        val accepted: Boolean,
        val jobId: UUID? = null,
        val scheduledAt: OffsetDateTime? = null,
        val reason: String? = null,
    )

    class NotificationRejectedException(reason: String?) :
        RuntimeException("notification-service rejected request (reason=$reason)")

    class NotificationServiceException(message: String) : RuntimeException(message)
}
