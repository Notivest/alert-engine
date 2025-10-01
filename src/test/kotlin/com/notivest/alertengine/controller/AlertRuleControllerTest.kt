package com.notivest.alertengine.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.alertengine.controllers.AlertRuleController
import com.notivest.alertengine.controllers.dto.alertrule.request.CreateAlertRuleRequest
import com.notivest.alertengine.controllers.dto.alertrule.request.GetAlertQuery
import com.notivest.alertengine.controllers.dto.alertrule.request.UpdateAlertRuleRequest
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.security.JwtUserIdResolver
import com.notivest.alertengine.service.interfaces.AlertRuleService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.delete
import java.util.*


@WebMvcTest(controllers = [AlertRuleController::class])
class AlertRuleControllerTest {

    @Autowired
    lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var service: AlertRuleService
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var userIdResolver: JwtUserIdResolver

    private val userId = UUID.randomUUID()

    @Test
    fun `GET - lista filtrada y paginada`() {
        whenever(userIdResolver.requireUserId(any())).thenReturn(userId)

        val pageable = PageRequest.of(0, 2)
        val rules = listOf(
            AlertRule(
                userId = userId,
                symbol = "AAPL",
                kind = AlertKind.PRICE_ABOVE,
                params = mapOf("price" to 100.0),
                timeframe = Timeframe.D1
            ),
            AlertRule(userId = userId, symbol = "AAPL", kind = AlertKind.PRICE_BELOW, params = mapOf("price" to 90.0), timeframe = Timeframe.D1)
        )
        whenever(service.list(eq(userId), any<GetAlertQuery>(), eq(pageable)))
            .thenReturn(PageImpl(rules.map { it }, pageable, 2))

        mvc.get("/alerts") {
            param("status", "ACTIVE")
            param("symbol", "AAPL")
            param("page", "0")
            param("size", "2")
            with(jwt()) // autenticado
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.content[0].symbol") { value("AAPL") }
                jsonPath("$.content.length()") { value(2) }
            }
    }

    @Test
    fun `POST - crea y devuelve 201`() {
        whenever(userIdResolver.requireUserId(any())).thenReturn(userId)

        val req = CreateAlertRuleRequest(
            symbol = "AAPL",
            kind = AlertKind.PRICE_ABOVE,
            params = mapOf("price" to 100.0),
            timeframe = Timeframe.D1,
            status = null,
            debounceSeconds = 15
        )

        val created = AlertRule(
            id = UUID.randomUUID(),
            userId = userId,
            symbol = req.symbol,
            kind = req.kind,
            params = req.params,
            timeframe = req.timeframe,
            status = RuleStatus.ACTIVE
        )

        whenever(service.create(eq(userId), eq(req))).thenReturn(created)

        mvc.post("/alerts") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(req)
            with(jwt())
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.symbol") { value("AAPL") }
                jsonPath("$.id") { value(created.id.toString()) }
            }
    }

    @Test
    fun `PATCH - update parcial`() {
        whenever(userIdResolver.requireUserId(any())).thenReturn(userId)

        val id = UUID.randomUUID()
        val req = UpdateAlertRuleRequest(
            params = mapOf("price" to 95.0),
            status = RuleStatus.PAUSED,
            debounceSeconds = 60
        )
        val updated = AlertRule(
            id = id,
            userId = userId,
            symbol = "AAPL",
            kind = AlertKind.PRICE_BELOW,
            params = req.params!!,
            timeframe = Timeframe.D1,
            status = RuleStatus.PAUSED
        )
        whenever(service.update(eq(userId), eq(id), eq(req))).thenReturn(updated)

        mvc.patch("/alerts/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(req)
            with(jwt())
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PAUSED") }
                jsonPath("$.id") { value(id.toString()) }
            }
    }

    @Test
    fun `DELETE - borrado lógico devuelve 204`() {
        whenever(userIdResolver.requireUserId(any())).thenReturn(userId)
        val id = UUID.randomUUID()

        mvc.delete("/alerts/$id") {
            with(jwt())
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `401 si no hay JWT`() {
        mvc.get("/alerts")
            .andExpect { status { isUnauthorized() } }
    }
}