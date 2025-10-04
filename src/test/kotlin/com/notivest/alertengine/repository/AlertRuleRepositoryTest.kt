package com.notivest.alertengine.repository

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.repositories.AlertRuleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID


@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertRuleRepositoryTest {

    @Autowired
    lateinit var ruleRepository: AlertRuleRepository

    private fun newRule(
        userId: UUID = UUID.randomUUID(),
        symbol: String = "AAPL",
        status: RuleStatus = RuleStatus.ACTIVE
    ): AlertRule =
        ruleRepository.saveAndFlush(
            AlertRule(
                userId = userId,
                symbol = symbol,
                kind = AlertKind.PRICE_THRESHOLD,
                params = mapOf("price" to 200.0),
                timeframe = Timeframe.D1,
                status = status
            )
        )

    @Test
    fun `findAllByUserIdAndStatus returns only that user's rules and supports pagination`() {
        val u1 = UUID.randomUUID()
        val u2 = UUID.randomUUID()


        repeat(3) { newRule(userId = u1, symbol = "U1_$it") }
        repeat(2) { newRule(userId = u2, symbol = "U2_$it") }


        val page = ruleRepository.findAllByUserIdAndStatus(u1, RuleStatus.ACTIVE, PageRequest.of(0, 10))


        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).allMatch { it.userId == u1 }
    }


    @Test
    fun `findByUserIdAndStatusOrderByUpdatedAtDesc returns ordered list`() {
        val u = UUID.randomUUID()
        val r1 = newRule(userId = u, symbol = "ONE")


        r1.status = RuleStatus.PAUSED
        ruleRepository.saveAndFlush(r1)


        val list = ruleRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(u, RuleStatus.PAUSED)
        assertThat(list).isNotEmpty
        assertThat(list.first().id).isEqualTo(r1.id)
    }
}
