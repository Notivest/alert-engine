package com.notivest.alertengine.notification

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.pricefetcher.tokenprovider.TokenPolicy
import com.notivest.alertengine.repositories.AlertRuleRepository
import io.mockk.mockk
import io.mockk.coEvery
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.UUID

class NotificationServiceImplTest {

    private val mapper = jacksonObjectMapper()
    private lateinit var server: MockWebServer
    private lateinit var properties: NotificationClientProperties
    private lateinit var client: WebClient
    private lateinit var tokenPolicy: TokenPolicy

    @BeforeEach
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val baseUrl = server.url("/").toString().removeSuffix("/")
        properties = NotificationClientProperties(
            baseUrl = baseUrl,
            alertPath = "/api/v1/notify/alert",
            alertTemplateKey = "alert-default"
        )
        client = WebClient.builder()
            .baseUrl(baseUrl)
            .build()
        tokenPolicy = mockk()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `send enqueues alert notification`() {
        val repository = mockk<AlertRuleRepository>(relaxed = true)
        val service = NotificationServiceImpl(client, properties, repository, tokenPolicy)
        val rule = buildRule()
        val event = buildEvent(rule)
        val jobId = UUID.randomUUID()
        val token = "token-123"

        coEvery { tokenPolicy.resolveToken() } returns token

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "accepted": true,
                      "jobId": "$jobId",
                      "scheduledAt": "2024-07-01T10:11:12Z"
                    }
                    """.trimIndent()
                )
        )

        runBlocking { service.send(event) }

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/v1/notify/alert")
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer $token")

        val payload = mapper.readTree(recorded.body.readUtf8())
        assertThat(payload["userId"].asText()).isEqualTo(rule.userId.toString())
        assertThat(payload["fingerprint"].asText()).isEqualTo(event.fingerprint)
        assertThat(payload["occurredAt"].asText()).isEqualTo(event.triggeredAt.toString())
        assertThat(payload["templateKey"].asText()).isEqualTo("alert-default")

        val templateData = payload["templateData"]
        assertThat(templateData["symbol"].asText()).isEqualTo(rule.symbol)
        assertThat(templateData["ruleKind"].asText()).isEqualTo(rule.kind.name)
        assertThat(templateData["ruleTitle"].asText()).isEqualTo(rule.title)
        assertThat(templateData["ruleNote"].asText()).isEqualTo(rule.note)
        assertThat(templateData["payload"]["price"].asDouble()).isEqualTo(123.45)
    }

    @Test
    fun `send throws when notification service rejects request`() {
        val repository = mockk<AlertRuleRepository>(relaxed = true)
        val service = NotificationServiceImpl(client, properties, repository, tokenPolicy)
        val rule = buildRule()
        val event = buildEvent(rule)

        coEvery { tokenPolicy.resolveToken() } returns null

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "accepted": false,
                      "reason": "contact_not_found"
                    }
                    """.trimIndent()
                )
        )

        val exception = assertThrows<NotificationServiceImpl.NotificationRejectedException> {
            runBlocking { service.send(event) }
        }
        assertThat(exception).hasMessageContaining("contact_not_found")

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/v1/notify/alert")
        assertThat(recorded.getHeader("Authorization")).isNull()
    }

    private fun buildRule(): AlertRule =
        AlertRule(
            userId = UUID.randomUUID(),
            symbol = "AAPL",
            title = "Alerta personalizada",
            note = "Nota para el usuario",
            kind = AlertKind.PRICE_THRESHOLD,
            params = mapOf("operator" to "GTE", "value" to 100),
            timeframe = Timeframe.D1,
            status = RuleStatus.ACTIVE
        )

    private fun buildEvent(rule: AlertRule): AlertEvent =
        AlertEvent(
            rule = rule,
            triggeredAt = OffsetDateTime.parse("2024-06-12T10:15:30Z"),
            payload = mapOf("price" to 123.45),
            fingerprint = "fp-123",
            severity = SeverityAlert.WARNING,
            sent = false
        )
}
