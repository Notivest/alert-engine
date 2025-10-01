package com.notivest.alertengine.service

import com.notivest.alertengine.controllers.dto.alertrule.request.CreateAlertRuleRequest
import com.notivest.alertengine.controllers.dto.alertrule.request.GetAlertQuery
import com.notivest.alertengine.controllers.dto.alertrule.request.UpdateAlertRuleRequest
import com.notivest.alertengine.exception.ForbiddenOperationException
import com.notivest.alertengine.exception.ResourceNotFoundException
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.repositories.AlertRuleRepository
import com.notivest.alertengine.service.implementations.AlertRuleServiceImpl
import com.notivest.alertengine.validation.AlertParamsValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import java.time.Duration
import java.util.Optional
import java.util.UUID

class AlertRuleServiceImplTest {

    private val repository: AlertRuleRepository = mock()
    private val validator: AlertParamsValidator = mock()
    private val service = AlertRuleServiceImpl(repository, validator)

    @Test
    fun `create - valida params y guarda con defaults`() {
        val userId = UUID.randomUUID()
        val req = CreateAlertRuleRequest(
            symbol = "AAPL",
            kind = AlertKind.PRICE_ABOVE,
            params = mapOf("price" to 100.0),
            timeframe = Timeframe.D1,
            status = null,                      // default ACTIVE
            debounceSeconds = 30
        )

        whenever(repository.saveAndFlush(any<AlertRule>()))
            .thenAnswer { inv -> inv.getArgument<AlertRule>(0) }

        val saved = service.create(userId, req)

        verify(validator).validate(req.kind, req.params)
        argumentCaptor<AlertRule>().apply {
            verify(repository).saveAndFlush(capture())
            val ent = firstValue
            assertThat(ent.userId).isEqualTo(userId)
            assertThat(ent.symbol).isEqualTo("AAPL")
            assertThat(ent.kind).isEqualTo(AlertKind.PRICE_ABOVE)
            assertThat(ent.timeframe).isEqualTo(Timeframe.D1)
            assertThat(ent.status).isEqualTo(RuleStatus.ACTIVE) // default aplicado
            assertThat(ent.debounceTime).isEqualTo(Duration.ofSeconds(30))
        }

        // devuelve lo que persiste
        assertThat(saved.symbol).isEqualTo("AAPL")
    }

    @Test
    fun `update - revalida si cambian params y aplica cambios parciales`() {
        val userId = UUID.randomUUID()
        val id = UUID.randomUUID()
        val existing = AlertRule(
            id = id,
            userId = userId,
            symbol = "AAPL",
            kind = AlertKind.PRICE_BELOW,
            params = mapOf("price" to 120.0),
            timeframe = Timeframe.H1,
            status = RuleStatus.ACTIVE
        )
        whenever(repository.findById(id)).thenReturn(Optional.of(existing))
        whenever(repository.saveAndFlush(any<AlertRule>()))
            .thenAnswer { inv -> inv.getArgument<AlertRule>(0) }

        val cmd = UpdateAlertRuleRequest(
            params = mapOf("price" to 110.0),
            status = RuleStatus.PAUSED,
            debounceSeconds = 90
        )

        val updated = service.update(userId, id, cmd)

        verify(validator).validate(existing.kind, cmd.params!!)
        assertThat(existing.params).isEqualTo(mapOf("price" to 110.0))
        assertThat(existing.status).isEqualTo(RuleStatus.PAUSED)
        assertThat(existing.debounceTime).isEqualTo(Duration.ofSeconds(90))
        // el servicio devuelve la misma instancia modificada
        assertThat(updated).isSameAs(existing)
    }

    @Test
    fun `setStatus - cambia estado cuando es distinto`() {
        val userId = UUID.randomUUID()
        val id = UUID.randomUUID()
        val rule = AlertRule(
            id = id, userId = userId, symbol = "AAPL",
            kind = AlertKind.PRICE_ABOVE, params = mapOf("price" to 100.0),
            timeframe = Timeframe.D1, status = RuleStatus.ACTIVE
        )
        whenever(repository.findById(id)).thenReturn(Optional.of(rule))

        val res = service.setStatus(userId, id, RuleStatus.DISABLED)

        assertThat(res.status).isEqualTo(RuleStatus.DISABLED)
        verify(repository).save(rule)
    }

    @Test
    fun `get - lanza 404 si no existe`() {
        val userId = UUID.randomUUID()
        val id = UUID.randomUUID()
        whenever(repository.findById(id)).thenReturn(Optional.empty())

        assertThrows(ResourceNotFoundException::class.java) {
            service.get(userId, id)
        }
    }

    @Test
    fun `operación de otro usuario - lanza 403`() {
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        val id = UUID.randomUUID()
        val rule = AlertRule(
            id = id, userId = owner, symbol = "AAPL",
            kind = AlertKind.PRICE_ABOVE, params = emptyMap(),
            timeframe = Timeframe.D1, status = RuleStatus.ACTIVE
        )
        whenever(repository.findById(id)).thenReturn(Optional.of(rule))

        assertThrows(ForbiddenOperationException::class.java) {
            service.update(other, id, UpdateAlertRuleRequest(status = RuleStatus.PAUSED))
        }
    }

    @Test
    fun `list - delega en repo con spec y pageable`() {
        val userId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 2)
        val query = GetAlertQuery(
            status = RuleStatus.ACTIVE,
            symbol = "AAPL",
            kind = null,
            timeframe = null,
        )
        val data = listOf(
            AlertRule(userId = userId, symbol = "AAPL", kind = AlertKind.PRICE_ABOVE, params = emptyMap(), timeframe = Timeframe.D1),
            AlertRule(userId = userId, symbol = "AAPL", kind = AlertKind.PRICE_BELOW, params = emptyMap(), timeframe = Timeframe.D1)
        )
        whenever(repository.findAll(any<Specification<AlertRule>>(), eq(pageable)))
            .thenReturn(PageImpl(data, pageable, data.size.toLong()))

        val page = service.list(userId, query, pageable)

        assertThat(page.totalElements).isEqualTo(2)
        verify(repository).findAll(any<Specification<AlertRule>>(), eq(pageable))
    }
}
