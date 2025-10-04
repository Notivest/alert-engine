package com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.evaluators.PriceThreshold
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PriceThresholdTest {

    private val evaluator = PriceThreshold()
    private val baseRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = "BTCUSDT",
        kind = AlertKind.PRICE_THRESHOLD,
        params = emptyMap(),
        timeframe = Timeframe.M1
    )

    private fun context(now: Instant) = RuleEvaluationContext(
        evaluatedAt = now,
        timeframe = Timeframe.M1
    )

    private fun prices(vararg candles: Candle) = DefaultPriceSeries(
        symbol = baseRule.symbol,
        timeframe = Timeframe.M1,
        candles = candles.toList()
    )

    @Test
    fun `evaluate triggers with critical severity and deterministic fingerprint`() {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val candle = Candle(
            openTime = now.minusSeconds(60),
            open = 100.0,
            high = 105.0,
            low = 99.5,
            close = 102.0
        )
        val params = PriceThresholdParams(
            operator = Operator.GTE,
            value = BigDecimal("100")
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(candle), params)
        val repeated = evaluator.evaluate(context(now), baseRule, prices(candle), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.CRITICAL)
        assertThat(result.fingerprint).matches("[0-9a-f]{64}")
        assertThat(result.fingerprint).isEqualTo(repeated.fingerprint)

        val payload = result.payload!!
        assertThat(payload.get("lastPrice").decimalValue()).isEqualByComparingTo(BigDecimal("102.0"))
        assertThat(payload.get("threshold").decimalValue()).isEqualByComparingTo(BigDecimal("100"))
        assertThat(payload.get("operator").asText()).isEqualTo("GTE")
        assertThat(payload.get("delta").decimalValue()).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(payload.get("asOf").asText()).isEqualTo(now.minusSeconds(60).toString())
    }

    @Test
    fun `evaluate triggers warning severity when overshoot is moderate`() {
        val now = Instant.parse("2024-01-01T00:05:00Z")
        val candle = Candle(
            openTime = now.minusSeconds(60),
            open = 100.0,
            high = 101.0,
            low = 99.8,
            close = 100.7
        )
        val params = PriceThresholdParams(
            operator = Operator.GTE,
            value = BigDecimal("100")
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(candle), params)

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.WARNING)
        assertThat(result.payload!!.get("delta").decimalValue()).isEqualByComparingTo(BigDecimal("0.7"))
    }

    @Test
    fun `evaluate returns no trigger when candles are stale`() {
        val now = Instant.parse("2024-01-01T00:10:00Z")
        val staleCandle = Candle(
            openTime = now.minusSeconds(300),
            open = 100.0,
            high = 100.0,
            low = 100.0,
            close = 120.0
        )
        val params = PriceThresholdParams(
            operator = Operator.GTE,
            value = BigDecimal("100")
        )

        val result = evaluator.evaluate(context(now), baseRule, prices(staleCandle), params)

        assertThat(result.triggered).isFalse()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.payload!!.get("lastPrice").isNull).isTrue()
        assertThat(result.payload!!.get("asOf").asText()).isEqualTo(now.minusSeconds(300).toString())
        assertThat(result.fingerprint).matches("[0-9a-f]{64}")
    }
}