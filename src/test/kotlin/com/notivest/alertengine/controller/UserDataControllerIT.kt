package com.notivest.alertengine.controller

import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.repositories.AlertRuleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("auth", "test")
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class UserDataControllerIT {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var alertRuleRepository: AlertRuleRepository

    @Autowired lateinit var alertEventRepository: AlertEventRepository

    @MockBean lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun setUp() {
        alertEventRepository.deleteAll()
        alertRuleRepository.deleteAll()
    }

    @Test
    fun `delete my user data removes only current user rows`() {
        val userOne = UUID.randomUUID()
        val userTwo = UUID.randomUUID()

        seedRuleAndEvent(userOne, "AAPL", "fp-user-1")
        seedRuleAndEvent(userTwo, "MSFT", "fp-user-2")

        mockMvc
            .perform(
                delete("/v1/user-data/me")
                    .with(
                        jwt().jwt { token ->
                            token.claim("user_id", userOne.toString())
                        },
                    ),
            ).andExpect(status().isNoContent)

        assertThat(alertRuleRepository.countByUserId(userOne)).isZero()
        assertThat(alertEventRepository.countByRuleUserId(userOne)).isZero()

        assertThat(alertRuleRepository.countByUserId(userTwo)).isEqualTo(1)
        assertThat(alertEventRepository.countByRuleUserId(userTwo)).isEqualTo(1)
    }

    @Test
    fun `delete my user data requires authentication`() {
        mockMvc
            .perform(delete("/v1/user-data/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `internal delete requires m2m scope`() {
        val userId = UUID.randomUUID()

        mockMvc
            .perform(
                delete("/internal/v1/user-data")
                    .queryParam("userId", userId.toString())
                    .with(jwt()),
            ).andExpect(status().isForbidden)

        mockMvc
            .perform(
                delete("/internal/v1/user-data")
                    .queryParam("userId", userId.toString())
                    .with(
                        jwt().authorities(
                            SimpleGrantedAuthority("SCOPE_portfolio:read:user-context"),
                        ),
                    ),
            ).andExpect(status().isNoContent)
    }

    private fun seedRuleAndEvent(
        userId: UUID,
        symbol: String,
        fingerprint: String,
    ) {
        val rule =
            alertRuleRepository.saveAndFlush(
                AlertRule(
                    userId = userId,
                    symbol = symbol,
                    kind = AlertKind.PRICE_THRESHOLD,
                    params = mapOf("price" to 100.0),
                    timeframe = Timeframe.D1,
                    status = RuleStatus.ACTIVE,
                    severity = SeverityAlert.INFO,
                ),
            )

        alertEventRepository.saveAndFlush(
            AlertEvent(
                rule = rule,
                triggeredAt = OffsetDateTime.now(),
                payload = mapOf("last" to 101.2),
                fingerprint = fingerprint,
                severity = SeverityAlert.INFO,
                sent = true,
            ),
        )
    }
}
