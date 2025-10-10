package com.notivest.alertengine.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.evaluators.pctchange.PctChangeBasis
import com.notivest.alertengine.ruleEvaluators.evaluators.pctchange.PctChangeEvaluator
import com.notivest.alertengine.ruleEvaluators.evaluators.pctchange.PctChangeParams
import com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold.Operator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PctChangeEvaluatorTest {

    private val evaluator = PctChangeEvaluator()
    private val baseRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = "ETHUSDT",
        kind = AlertKind.PCT_CHANGE,
        params = emptyMap(),
        timeframe = Timeframe.M5
    )

    private fun context(now: Instant) = RuleEvaluationContext(
        evaluatedAt = now,
        timeframe = baseRule.timeframe
    )

    private fun prices(vararg candles: Candle) = DefaultPriceSeries(
        symbol = baseRule.symbol,
        timeframe = baseRule.timeframe,
        candles = candles.toList()
    )

    @Test
    fun `triggers when percent gain meets threshold`() {
        val now = Instant.parse("2024-03-01T00:15:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(900), 100.0, 105.0, 99.0, 100.0),
            Candle(now.minusSeconds(600), 101.0, 106.0, 100.0, 102.0),
            Candle(now.minusSeconds(300), 103.0, 109.0, 102.0, 110.0),
        )
        val params = PctChangeParams(
            operator = Operator.GTE,
            pct = 8.0,
            lookbackBars = 2,
            basis = PctChangeBasis.CLOSE,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(*candles), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.reason).isEqualTo("PCT_CHANGE +10.00% GTE +8.00%")
        assertThat(result.fingerprint).matches("[0-9a-f]{64}")

        val payload = result.payload!!
        assertThat(payload.get("actualPct").asDouble()).isCloseTo(10.0, within(1e-6))
        assertThat(payload.get("thresholdPct").asDouble()).isEqualTo(8.0)
        assertThat(payload.get("symbol").asText()).isEqualTo(baseRule.symbol)
        assertThat(payload.get("basis").asText()).isEqualTo("CLOSE")
        assertThat(payload.get("fromTs").asText()).isEqualTo(now.minusSeconds(900).toString())
        assertThat(payload.get("toTs").asText()).isEqualTo(now.minusSeconds(300).toString())
    }

    @Test
    fun `does not trigger when change below threshold`() {
        val now = Instant.parse("2024-03-01T01:00:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(300), 100.0, 101.0, 99.0, 100.0),
            Candle(now, 100.0, 101.5, 99.5, 102.5),
        )
        val params = PctChangeParams(
            operator = Operator.GT,
            pct = 3.0,
            lookbackBars = 1,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(*candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.payload!!.get("actualPct").asDouble()).isCloseTo(2.5, within(1e-6))
    }

    @Test
    fun `triggers for negative thresholds when drop is large enough`() {
        val now = Instant.parse("2024-03-01T02:00:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(300), 100.0, 101.0, 99.0, 95.0),
            Candle(now, 95.0, 96.0, 90.0, 89.3),
        )
        val params = PctChangeParams(
            operator = Operator.LTE,
            pct = -5.0,
            lookbackBars = 1,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(*candles), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.reason).isEqualTo("PCT_CHANGE -6.00% LTE -5.00%")
        assertThat(result.payload!!.get("actualPct").asDouble()).isCloseTo(-6.0, within(1e-6))
    }

    @Test
    fun `returns no trigger when insufficient candles`() {
        val now = Instant.parse("2024-03-01T03:00:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(300), 100.0, 101.0, 99.0, 100.0),
        )
        val params = PctChangeParams(
            operator = Operator.GTE,
            pct = 1.0,
            lookbackBars = 2,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(*candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        assertThat(result.payload!!.get("actualPct").isNull).isTrue()
    }

    @Test
    fun `returns no trigger when denominator close to zero`() {
        val now = Instant.parse("2024-03-01T04:00:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(300), 0.0, 0.0, 0.0, 0.0),
            Candle(now, 1.0, 1.0, 1.0, 1.0),
        )
        val params = PctChangeParams(
            operator = Operator.GTE,
            pct = 10.0,
            lookbackBars = 1,
            basis = PctChangeBasis.HL2,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(*candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        val payload = result.payload!!
        assertThat(payload.get("actualPct").isNull).isTrue()
        assertThat(payload.get("fromTs").asText()).isEqualTo(now.minusSeconds(300).toString())
        assertThat(payload.get("basis").asText()).isEqualTo("HL2")
    }
}