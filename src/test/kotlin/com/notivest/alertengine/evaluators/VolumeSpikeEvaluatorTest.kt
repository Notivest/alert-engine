package com.notivest.alertengine.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.evaluators.volumeSpike.VolumeSpikeEvaluator
import com.notivest.alertengine.ruleEvaluators.evaluators.volumeSpike.VolumeSpikeOperator
import com.notivest.alertengine.ruleEvaluators.evaluators.volumeSpike.VolumeSpikeParams
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class VolumeSpikeEvaluatorTest {

    private val evaluator = VolumeSpikeEvaluator()
    private val baseRule = AlertRule(
        userId = UUID.randomUUID(),
        symbol = "MSFT",
        kind = AlertKind.VOLUME_SPIKE,
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

    private fun candlesFromVolumes(start: Instant, volumes: List<Double>, stepSeconds: Long = 300L): List<Candle> =
        volumes.mapIndexed { idx, volume ->
            val ts = start.plusSeconds(idx * stepSeconds)
            Candle(ts, 1.0, 1.0, 1.0, 1.0, volume)
        }

    @Test
    fun `triggers when current volume exceeds moving average multiplier`() {
        val start = Instant.parse("2024-07-01T09:30:00Z")
        val lookback = 5
        val historyVolumes = List(lookback) { 100.0 }
        val currentVolume = 250.0
        val candles = candlesFromVolumes(start, historyVolumes + currentVolume)
        val params = VolumeSpikeParams(
            lookbackRaw = lookback,
            multiplierRaw = 2.0,
            operator = VolumeSpikeOperator.ABOVE_MA,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles),
            params,
        )

        assertThat(result.triggered).isTrue()
        assertThat(result.severity).isEqualTo(SeverityAlert.INFO)
        assertThat(result.reason).isEqualTo("VOLUME_SPIKE ABOVE_MA volume=250.00 threshold=200.00")
        val payload = result.payload!!
        assertThat(payload.get("currentVolume").asDouble()).isEqualTo(currentVolume)
        assertThat(payload.get("baselineVolume").asDouble()).isEqualTo(100.0)
        assertThat(payload.get("thresholdVolume").asDouble()).isEqualTo(200.0)
    }

    @Test
    fun `does not trigger when sustained zero volume baseline`() {
        val start = Instant.parse("2024-07-01T09:30:00Z")
        val lookback = 5
        val historyVolumes = List(lookback) { 0.0 }
        val currentVolume = 0.0
        val candles = candlesFromVolumes(start, historyVolumes + currentVolume)
        val params = VolumeSpikeParams(
            lookbackRaw = lookback,
            multiplierRaw = 2.5,
            operator = VolumeSpikeOperator.ABOVE_MA,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles),
            params,
        )

        assertThat(result.triggered).isFalse()
        assertThat(result.reason).isNull()
        val payload = result.payload!!
        assertThat(payload.get("note").asText()).isEqualTo("baseline_non_positive")
        assertThat(payload.get("baselineVolume").asDouble()).isEqualTo(0.0)
        assertThat(payload.get("thresholdVolume").isNull).isTrue()
    }

    @Test
    fun `triggers when volume reaches percentile threshold`() {
        val start = Instant.parse("2024-07-01T09:30:00Z")
        val lookback = 10
        val historyVolumes = (0 until lookback).map { 10.0 + it }
        val paramsPercentile = 0.9
        val candles = candlesFromVolumes(start, historyVolumes + 18.1)
        val params = VolumeSpikeParams(
            lookbackRaw = lookback,
            percentileRaw = paramsPercentile,
            operator = VolumeSpikeOperator.ABOVE_PCTL,
        )

        val result = evaluator.evaluate(
            context(candles.last().openTime),
            baseRule,
            prices(candles),
            params,
        )

        assertThat(result.triggered).isTrue()
        assertThat(result.reason).contains("ABOVE_PCTL")
        val payload = result.payload!!
        assertThat(payload.get("thresholdVolume").asDouble())
            .isCloseTo(18.1, Offset.offset(1e-6))
        assertThat(payload.get("currentVolume").asDouble()).isEqualTo(18.1)
    }
}
