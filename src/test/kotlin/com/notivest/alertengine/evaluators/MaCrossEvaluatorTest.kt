package com.notivest.alertengine.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.evaluators.macross.MaCrossDirection
import com.notivest.alertengine.ruleEvaluators.evaluators.macross.MaCrossEvaluator
import com.notivest.alertengine.ruleEvaluators.evaluators.macross.MaCrossParams
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale
import java.util.UUID

class MaCrossEvaluatorTest {

    private val evaluator = MaCrossEvaluator()
    private val baseRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = "BTCUSDT",
        kind = AlertKind.MA_CROSS,
        params = emptyMap(),
        timeframe = Timeframe.M5,
    )

    private fun context(now: Instant) = RuleEvaluationContext(
        evaluatedAt = now,
        timeframe = baseRule.timeframe,
    )

    private fun prices(candles: List<Candle>) = DefaultPriceSeries(
        symbol = baseRule.symbol,
        timeframe = baseRule.timeframe,
        candles = candles,
    )

    private fun candlesFromCloses(start: Instant, closes: List<Double>): List<Candle> =
        closes.mapIndexed { idx, close ->
            val ts = start.plusSeconds(idx * 300L)
            Candle(ts, close, close, close, close)
        }

    @Test
    fun `triggers when fast average crosses above slow`() {
        val start = Instant.parse("2024-05-01T00:00:00Z")
        val closes = listOf(100.0, 99.0, 98.0, 97.0, 96.0, 110.0)
        val candles = candlesFromCloses(start, closes)
        val params = MaCrossParams(fast = 3, slow = 5, direction = MaCrossDirection.UP)

        val result = evaluator.evaluate(context(candles.last().openTime), baseRule, prices(candles), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.reason).isEqualTo(
            String.format(Locale.US, "MA_CROSS %s fast=%.4f slow=%.4f", "UP", 101.0, 100.0)
        )
        assertThat(result.payload!!.get("direction").asText()).isEqualTo("UP")
        assertThat(result.payload!!.get("fastMa").asDouble()).isEqualTo(101.0)
        assertThat(result.payload!!.get("slowMa").asDouble()).isEqualTo(100.0)
        assertThat(result.fingerprint).matches("[0-9a-f]{64}")
    }

    @Test
    fun `triggers when fast average crosses below slow`() {
        val start = Instant.parse("2024-05-02T00:00:00Z")
        val closes = listOf(100.0, 101.0, 102.0, 103.0, 104.0, 90.0)
        val candles = candlesFromCloses(start, closes)
        val params = MaCrossParams(fast = 3, slow = 5, direction = MaCrossDirection.DOWN)

        val result = evaluator.evaluate(context(candles.last().openTime), baseRule, prices(candles), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.reason).isEqualTo(
            String.format(Locale.US, "MA_CROSS %s fast=%.4f slow=%.4f", "DOWN", 99.0, 100.0)
        )
        assertThat(result.payload!!.get("previousFastMa").asDouble()).isEqualTo(103.0)
        assertThat(result.payload!!.get("previousSlowMa").asDouble()).isEqualTo(102.0)
    }

    @Test
    fun `does not trigger when direction mismatches crossover`() {
        val start = Instant.parse("2024-05-01T00:00:00Z")
        val closes = listOf(100.0, 99.0, 98.0, 97.0, 96.0, 110.0)
        val candles = candlesFromCloses(start, closes)
        val params = MaCrossParams(fast = 3, slow = 5, direction = MaCrossDirection.DOWN)

        val result = evaluator.evaluate(context(candles.last().openTime), baseRule, prices(candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
    }

    @Test
    fun `returns no trigger when insufficient candles`() {
        val start = Instant.parse("2024-05-03T00:00:00Z")
        val closes = listOf(100.0, 101.0, 102.0, 103.0, 104.0)
        val candles = candlesFromCloses(start, closes)
        val params = MaCrossParams(fast = 3, slow = 5, direction = MaCrossDirection.UP)

        val result = evaluator.evaluate(context(candles.last().openTime), baseRule, prices(candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.payload!!.get("fastMa").isNull).isTrue()
        assertThat(result.payload!!.get("slowMa").isNull).isTrue()
    }
}
