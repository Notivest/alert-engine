package com.notivest.alertengine.controller

import com.notivest.alertengine.controllers.AlertEventController
import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.security.JwtUserIdResolver
import com.notivest.alertengine.service.interfaces.AlertEventService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@WebMvcTest(controllers = [AlertEventController::class])
class AlertEventControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var service: AlertEventService

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var userIdResolver: JwtUserIdResolver

    private val userId = UUID.randomUUID()

    @Test
    fun `GET - retorna eventos paginados del usuario`() {
        whenever(userIdResolver.requireUserId(any())).thenReturn(userId)

        val pageable = PageRequest.of(1, 2)
        val rule = AlertRule(
            id = UUID.randomUUID(),
            userId = userId,
            symbol = "AAPL",
            kind = AlertKind.PRICE_THRESHOLD,
            params = mapOf("price" to 150.0),
            timeframe = Timeframe.D1,
            status = RuleStatus.ACTIVE,
        )
        val event = AlertEvent(
            id = UUID.randomUUID(),
            rule = rule,
            triggeredAt = OffsetDateTime.of(2024, 1, 10, 9, 30, 0, 0, ZoneOffset.UTC),
            payload = mapOf("last" to 152.0),
            fingerprint = "price-spike",
            severity = SeverityAlert.WARNING,
            sent = true,
        )
        val page: Page<AlertEvent> = PageImpl(listOf(event), pageable, 3)

        whenever(service.list(eq(userId), any())).thenReturn(page)

        mvc.get("/alert-events") {
            param("page", "1")
            param("size", "2")
            with(jwt())
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.content[0].id") { value(event.id.toString()) }
                jsonPath("$.content[0].severity") { value("WARNING") }
                jsonPath("$.totalElements") { value(3) }
            }

        argumentCaptor<Pageable>().apply {
            verify(service).list(eq(userId), capture())
            val captured = firstValue
            assertThat(captured.pageNumber).isEqualTo(1)
            assertThat(captured.pageSize).isEqualTo(2)
        }
    }

    @Test
    fun `401 si no hay JWT`() {
        mvc.get("/alert-events")
            .andExpect { status { isUnauthorized() } }
    }
}