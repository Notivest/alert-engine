package com.notivest.alertengine.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.evaluators.rsi.RsiEvaluator
import com.notivest.alertengine.ruleEvaluators.evaluators.rsi.RsiOperator
import com.notivest.alertengine.ruleEvaluators.evaluators.rsi.RsiParams
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RsiEvaluatorTest {

    private val evaluator = RsiEvaluator()
    private val baseRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = "ETHUSDT",
        kind = AlertKind.RSI,
        params = emptyMap(),
        timeframe = Timeframe.M5,
    )

    private fun context(now: Instant) = RuleEvaluationContext(
        evaluatedAt = now,
        timeframe = baseRule.timeframe,
    )

    private fun prices(candles: List<Candle>, timeframe: Timeframe = baseRule.timeframe) = DefaultPriceSeries(
        symbol = baseRule.symbol,
        timeframe = timeframe,
        candles = candles,
    )

    private fun candlesFromCloses(start: Instant, closes: List<Double>, stepSeconds: Long = 300L): List<Candle> =
        closes.mapIndexed { idx, close ->
            val ts = start.plusSeconds(idx * stepSeconds)
            Candle(ts, close, close, close, close)
        }

    private fun rampCloses(length: Int, start: Double = 100.0, step: Double = 1.0): List<Double> =
        (0 until length).map { idx -> start + (step * idx) }

    @Test
    fun `triggers when RSI is above threshold`() {
        val start = Instant.parse("2024-06-01T00:00:00Z")
        val period = 14
        val closes = rampCloses(period + RsiParams.WARMUP_BARS + 5, start = 200.0)
        val candles = candlesFromCloses(start, closes)
        val params = RsiParams(
            period = period,
            threshold = 70.0,
            operator = RsiOperator.ABOVE,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles),
            params,
        )

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.reason).isEqualTo("RSI 100.00 ABOVE 70.00")
        assertThat(result.payload!!.get("rsi").asDouble()).isEqualTo(100.0)
        assertThat(result.payload!!.get("previousRsi").asDouble()).isEqualTo(100.0)
        assertThat(result.payload!!.get("period").asInt()).isEqualTo(period)
        assertThat(result.payload!!.get("threshold").asDouble()).isEqualTo(70.0)
        assertThat(result.fingerprint).matches("[0-9a-f]{64}")
    }

    @Test
    fun `triggers when RSI crosses down the threshold`() {
        val start = Instant.parse("2024-06-02T00:00:00Z")
        val period = 14
        val stableCloses = rampCloses(period + RsiParams.WARMUP_BARS + 1, start = 300.0)
        val preDrop = stableCloses.last() + 2.0
        val finalDrop = preDrop - 40.0
        val closes = stableCloses + listOf(preDrop, finalDrop)
        val candles = candlesFromCloses(start, closes)
        val params = RsiParams(
            period = period,
            threshold = 70.0,
            operator = RsiOperator.CROSSING_DOWN,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles),
            params,
        )

        assertThat(result.triggered).isTrue()
        assertThat(result.reason).contains("CROSSING_DOWN 70.00")
        val currentRsi = result.payload!!.get("rsi").asDouble()
        val previousRsi = result.payload!!.get("previousRsi").asDouble()
        assertThat(previousRsi).isGreaterThan(70.0)
        assertThat(currentRsi).isLessThan(70.0)
    }

    @Test
    fun `does not trigger when there are not enough candles`() {
        val start = Instant.parse("2024-06-03T00:00:00Z")
        val period = 14
        val closes = rampCloses(period + 5, start = 150.0)
        val candles = candlesFromCloses(start, closes)
        val params = RsiParams(
            period = period,
            threshold = 30.0,
            operator = RsiOperator.BELOW,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles),
            params,
        )

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        assertThat(result.payload!!.get("rsi").isNull).isTrue()
        assertThat(result.payload!!.get("note").asText()).isEqualTo("insufficient_candles")
    }

    @Test
    fun `does not trigger when timeframe override mismatches`() {
        val start = Instant.parse("2024-06-04T00:00:00Z")
        val period = 14
        val closes = rampCloses(period + RsiParams.WARMUP_BARS + 5, start = 120.0)
        val candles = candlesFromCloses(start, closes)
        val params = RsiParams(
            period = period,
            threshold = 60.0,
            operator = RsiOperator.ABOVE,
            timeframe = Timeframe.M15,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles, timeframe = Timeframe.M5),
            params,
        )

        assertThat(result.triggered).isFalse()
        assertThat(result.payload!!.get("note").asText()).isEqualTo("timeframe_mismatch")
    }
}
