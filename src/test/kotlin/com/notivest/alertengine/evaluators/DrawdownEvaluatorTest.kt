package com.notivest.alertengine.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.evaluators.drawdown.DrawdownEvaluator
import com.notivest.alertengine.ruleEvaluators.evaluators.drawdown.DrawdownParams
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DrawdownEvaluatorTest {

    private val evaluator = DrawdownEvaluator()
    private val baseRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = "BTCUSDT",
        kind = AlertKind.DRAWDOWN,
        params = emptyMap(),
        timeframe = Timeframe.M5,
    )

    private fun context(now: Instant) = RuleEvaluationContext(
        evaluatedAt = now,
        timeframe = baseRule.timeframe,
    )

    private fun prices(
        candles: Array<Candle>,
        currentPrice: Double? = null,
        currentPriceAsOf: Instant? = null,
    ) = DefaultPriceSeries(
        symbol = baseRule.symbol,
        timeframe = baseRule.timeframe,
        candles = candles.toList(),
        currentPrice = currentPrice,
        currentPriceAsOf = currentPriceAsOf,
    )

    @Test
    fun `triggers when drawdown exceeds threshold`() {
        val now = Instant.parse("2024-04-01T10:15:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(900), 108.0, 110.0, 106.0, 108.0),
            Candle(now.minusSeconds(600), 118.0, 120.0, 115.0, 118.0),
            Candle(now.minusSeconds(300), 110.0, 119.0, 104.0, 106.0),
        )
        val params = DrawdownParams(
            threshold = 10.0,
            lookback = 3,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(candles), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.reason).isEqualTo("DRAWDOWN 11.67% >= 10.00%")
        assertThat(result.fingerprint).matches("[0-9a-f]{64}")

        val payload = result.payload!!
        assertThat(payload.get("maxPrice").asDouble()).isEqualTo(120.0)
        assertThat(payload.get("currentPrice").asDouble()).isEqualTo(106.0)
        assertThat(payload.get("drawdownPct").asDouble()).isCloseTo(11.6666667, within(1e-6))
        assertThat(payload.get("fromTs").asText()).isEqualTo(now.minusSeconds(900).toString())
        assertThat(payload.get("barTs").asText()).isEqualTo(now.minusSeconds(300).toString())
    }

    @Test
    fun `does not trigger when drawdown is below threshold`() {
        val now = Instant.parse("2024-04-01T11:00:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(900), 109.0, 110.0, 108.0, 109.0),
            Candle(now.minusSeconds(600), 111.0, 112.0, 110.0, 111.0),
            Candle(now.minusSeconds(300), 110.0, 111.0, 107.0, 108.0),
        )
        val params = DrawdownParams(
            threshold = 5.0,
            lookback = 3,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        val payload = result.payload!!
        assertThat(payload.get("maxPrice").asDouble()).isEqualTo(112.0)
        assertThat(payload.get("currentPrice").asDouble()).isEqualTo(108.0)
        assertThat(payload.get("drawdownPct").asDouble()).isCloseTo(3.5714286, within(1e-6))
    }

    @Test
    fun `does not trigger when candles are insufficient`() {
        val now = Instant.parse("2024-04-01T12:00:00Z")
        val candles = arrayOf(
            Candle(now.minusSeconds(600), 100.0, 101.0, 99.0, 100.0),
            Candle(now.minusSeconds(300), 100.0, 102.0, 99.0, 101.0),
        )
        val params = DrawdownParams(
            threshold = 8.0,
            lookback = 3,
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(candles), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        val payload = result.payload!!
        assertThat(payload.get("note").asText()).isEqualTo("insufficient_candles")
        assertThat(payload.get("drawdownPct").isNull).isTrue()
    }

    @Test
    fun `uses realtime current price when available`() {
        val now = Instant.parse("2024-04-01T13:00:00Z")
        val quoteTs = now.plusSeconds(60)
        val candles = arrayOf(
            Candle(now.minusSeconds(900), 108.0, 110.0, 106.0, 108.0),
            Candle(now.minusSeconds(600), 118.0, 120.0, 115.0, 118.0),
            Candle(now.minusSeconds(300), 112.0, 119.0, 110.0, 112.0),
        )
        val params = DrawdownParams(
            threshold = 10.0,
            lookback = 3,
        )

        val series = prices(
            candles = candles,
            currentPrice = 106.0,
            currentPriceAsOf = quoteTs,
        )
        val result = evaluator.evaluate(context(now), baseRule, series, params)

        assertThat(result.triggered).isTrue()
        assertThat(result.payload!!.get("currentPrice").asDouble()).isEqualTo(106.0)
        assertThat(result.payload!!.get("barTs").asText()).isEqualTo(quoteTs.toString())
    }
}
