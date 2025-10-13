package com.notivest.alertengine.service


import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.service.implementations.AlertEventServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class AlertEventServiceImplTest {

    private val repository: AlertEventRepository = mock()
    private val service = AlertEventServiceImpl(repository)

    @Test
    fun `list delegates to repository`() {
        val userId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 5)

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
        val page = PageImpl(listOf(event), pageable, 1)

        whenever(repository.findAllByRuleUserId(eq(userId), eq(pageable))).thenReturn(page)

        val result = service.list(userId, pageable)

        assertThat(result).isSameAs(page)
        verify(repository).findAllByRuleUserId(eq(userId), eq(pageable))
    }
}