package com.notivest.alertengine.repository

import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.repositories.AlertRuleRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertEventRepositoryTest {

    @Autowired
    lateinit var ruleRepository: AlertRuleRepository

    @Autowired
    lateinit var eventRepository: AlertEventRepository

    private fun newRule(): AlertRule =
        ruleRepository.saveAndFlush(
            AlertRule(
                userId = UUID.randomUUID(),
                symbol = "AAPL",
                kind = AlertKind.PRICE_ABOVE,
                params = mapOf("price" to 200.0),
                timeframe = Timeframe.D1,
                status = RuleStatus.ACTIVE
            )
        )

    private fun addEvent(rule: AlertRule, fp: String): AlertEvent =
        eventRepository.saveAndFlush(
            AlertEvent(
                rule = rule,
                triggeredAt = OffsetDateTime.now(ZoneOffset.UTC),
                payload = mapOf("last" to 201.5),
                fingerprint = fp,
                sent = false
            )
        )


    @Test
    fun `findAllByRuleId supports pagination`() {
        val rule = newRule()
        repeat(3) { addEvent(rule, fp = "fp-$it") }


        val page = eventRepository.findAllByRuleId(rule.id, PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).allMatch { it.rule.id == rule.id }
    }


    @Test
    fun `unique constraint on (rule_id, fingerprint) raises DataIntegrityViolationException`() {
        val rule = newRule()
        addEvent(rule, fp = "dup")


        assertThatThrownBy { addEvent(rule, fp = "dup") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }


    @Test
    fun `existsByRuleIdAndFingerprint returns true for inserted event`() {
        val rule = newRule()
        val fp = "exists"
        addEvent(rule, fp)


        assertThat(eventRepository.existsByRuleIdAndFingerprint(rule.id, fp)).isTrue()
    }
}