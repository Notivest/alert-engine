package com.notivest.alertengine.scheduler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.pricefetcher.client.PriceDataClient
import com.notivest.alertengine.pricefetcher.dto.CandleDTO
import com.notivest.alertengine.ruleEvaluators.RuleEvaluationOrchestrator
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.repositories.AlertRuleRepository
import com.notivest.alertengine.service.interfaces.NotificationService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.ResourcelessTransactionManager
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AlertEvaluationJobTest {

    private lateinit var alertRuleRepository: AlertRuleRepository
    private lateinit var alertEventRepository: AlertEventRepository
    private lateinit var priceDataClient: PriceDataClient
    private lateinit var orchestrator: RuleEvaluationOrchestrator
    private lateinit var notificationService: NotificationService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var properties: AlertEvaluationSchedulerProperties
    private lateinit var transactionManager: PlatformTransactionManager
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        alertRuleRepository = mockk(relaxed = true)
        alertEventRepository = mockk(relaxed = true)
        priceDataClient = mockk()
        orchestrator = mockk()
        notificationService = mockk()
        meterRegistry = SimpleMeterRegistry()
        properties = AlertEvaluationSchedulerProperties().apply {
            historyLookback = 5
            maxParallelEvaluations = 4
        }
        transactionManager = ResourcelessTransactionManager()
    }

    @Test
    fun `groups rules per symbol-timeframe and triggers events`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = newJob(dispatcher)

        val now = Instant.parse("2024-01-01T00:00:00Z")
        val rule1 = activeRule(symbol = "AAPL")
        val rule2 = activeRule(symbol = "AAPL")
        every { alertRuleRepository.findAllByStatus(RuleStatus.ACTIVE) } returns listOf(rule1, rule2)

        val candles = listOf(
            candle(Instant.parse("2023-12-31T23:00:00Z")),
            candle(Instant.parse("2023-12-31T23:05:00Z")),
        )
        coEvery {
            priceDataClient.getHistorical(
                symbol = "AAPL",
                timeframe = com.notivest.alertengine.pricefetcher.dto.Timeframe.D1,
                limit = properties.historyLookback,
            )
        } returns candles

        every { orchestrator.evaluate(any(), rule1, any()) } returns RuleEvaluationResult(
            triggered = false,
            severity = SeverityAlert.INFO,
            fingerprint = "fp-1",
            payload = null,
        )

        every { orchestrator.evaluate(any(), rule2, any()) } returns RuleEvaluationResult(
            triggered = true,
            severity = SeverityAlert.CRITICAL,
            fingerprint = "fp-2",
            payload = null,
        )

        every { alertEventRepository.existsByRuleIdAndFingerprint(any(), any()) } returns false
        every { alertEventRepository.save(any()) } answers { firstArg() }
        every { alertRuleRepository.save(any()) } answers { firstArg() }
        justRun { notificationService.send(any()) }

        job.execute(UUID.randomUUID(), now)

        coVerify(exactly = 1) {
            priceDataClient.getHistorical(
                symbol = "AAPL",
                timeframe = com.notivest.alertengine.pricefetcher.dto.Timeframe.D1,
                limit = properties.historyLookback,
            )
        }

        verify { alertEventRepository.save(match { it.fingerprint == "fp-2" && it.rule == rule2 }) }
        verify { notificationService.send(match { it.fingerprint == "fp-2" }) }

        assertThat(meterRegistry.counter("alertengine.scheduler.rules.evaluated").count()).isEqualTo(2.0)
        assertThat(meterRegistry.counter("alertengine.scheduler.alerts.triggered").count()).isEqualTo(1.0)
        assertThat(meterRegistry.counter("alertengine.scheduler.groups.processed").count()).isEqualTo(1.0)
    }

    @Test
    fun `applies debounce before persisting`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = newJob(dispatcher)

        val rule = activeRule(
            debounce = Duration.ofMinutes(5),
            lastTriggered = OffsetDateTime.ofInstant(Instant.parse("2024-01-01T00:00:00Z").minusSeconds(60), ZoneOffset.UTC),
        )
        every { alertRuleRepository.findAllByStatus(RuleStatus.ACTIVE) } returns listOf(rule)
        coEvery {
            priceDataClient.getHistorical(
                symbol = any(),
                timeframe = any(),
                limit = any(),
            )
        } returns listOf(candle(Instant.now()))

        every { orchestrator.evaluate(any(), rule, any()) } returns RuleEvaluationResult(
            triggered = true,
            severity = SeverityAlert.INFO,
            fingerprint = "debounce",
            payload = null,
        )

        every { alertEventRepository.existsByRuleIdAndFingerprint(rule.id, "debounce") } returns false

        job.execute(UUID.randomUUID(), Instant.parse("2024-01-01T00:01:00Z"))

        verify(exactly = 0) { alertEventRepository.save(any()) }
        verify(exactly = 0) { notificationService.send(any()) }
    }

    @Test
    fun `applies idempotency based on fingerprint`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = newJob(dispatcher)

        val rule = activeRule()
        every { alertRuleRepository.findAllByStatus(RuleStatus.ACTIVE) } returns listOf(rule)
        coEvery {
            priceDataClient.getHistorical(
                symbol = any(),
                timeframe = any(),
                limit = any(),
            )
        } returns listOf(candle(Instant.now()))

        every { orchestrator.evaluate(any(), rule, any()) } returns RuleEvaluationResult(
            triggered = true,
            severity = SeverityAlert.INFO,
            fingerprint = "duplicate",
            payload = null,
        )

        every { alertEventRepository.existsByRuleIdAndFingerprint(rule.id, "duplicate") } returns true

        job.execute(UUID.randomUUID(), Instant.parse("2024-01-01T00:00:00Z"))

        verify(exactly = 0) { alertEventRepository.save(any()) }
        verify(exactly = 0) { notificationService.send(any()) }
    }

    private fun newJob(dispatcher: StandardTestDispatcher): AlertEvaluationJob {
        val errorMetrics = ErrorMetrics(meterRegistry)
        val groupProcessor = GroupProcessor(priceDataClient, properties, meterRegistry, errorMetrics)
        val ruleRunner = RuleRunner(orchestrator, meterRegistry, errorMetrics)
        val eventSink = EventSink(
            alertEventRepository,
            alertRuleRepository,
            notificationService,
            meterRegistry,
            objectMapper,
            errorMetrics,
            transactionManager,
        )
        return AlertEvaluationJob(
            alertRuleRepository,
            properties,
            groupProcessor,
            ruleRunner,
            eventSink,
            meterRegistry,
            dispatcher,
        )
    }

    private fun activeRule(
        symbol: String = "AAPL",
        debounce: Duration? = null,
        lastTriggered: OffsetDateTime? = null,
    ): AlertRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = symbol,
        kind = AlertKind.PRICE_CHANGE,
        timeframe = Timeframe.D1,
        status = RuleStatus.ACTIVE,
    ).apply {
        debounceTime = debounce
        lastTriggeredAt = lastTriggered
    }

    private fun candle(openTime: Instant): CandleDTO = CandleDTO(
        ts = openTime,
        open = BigDecimal.TEN,
        high = BigDecimal.TEN,
        low = BigDecimal.ONE,
        close = BigDecimal.ONE,
        volume = BigDecimal.ONE,
    )
}
