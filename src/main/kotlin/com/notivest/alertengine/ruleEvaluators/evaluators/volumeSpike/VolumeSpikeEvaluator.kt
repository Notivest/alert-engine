package com.notivest.alertengine.ruleEvaluators.evaluators.volumeSpike

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.PriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import com.notivest.alertengine.ruleEvaluators.evaluators.RuleEvaluator
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.reflect.KClass

@Component
class VolumeSpikeEvaluator : RuleEvaluator<VolumeSpikeParams> {

    override fun getKind(): AlertKind = AlertKind.VOLUME_SPIKE

    override fun getParamsType(): KClass<VolumeSpikeParams> = VolumeSpikeParams::class

    override fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: VolumeSpikeParams,
    ): RuleEvaluationResult {
        val candles = prices.candles
        if (candles.size < params.requiredCandles()) {
            val last = candles.lastOrNull()
            return noTrigger(rule, params, last?.openTime, null, null, "insufficient_candles")
        }

        val lastCandle = candles.last()
        val currentVolume = lastCandle.volume
        if (currentVolume == null || !currentVolume.isFinite() || currentVolume < 0.0) {
            return noTrigger(rule, params, lastCandle.openTime, null, currentVolume, "current_volume_unavailable")
        }

        val lookback = params.resolvedLookback()
        val historyStart = candles.size - 1 - lookback
        val historyCandles = candles.subList(historyStart, candles.size - 1)
        val historyVolumes = extractVolumes(historyCandles)
            ?: return noTrigger(rule, params, lastCandle.openTime, null, currentVolume, "history_volume_unavailable")

        val (threshold, baseline) = when (params.operator) {
            VolumeSpikeOperator.ABOVE_MA -> {
                val average = historyVolumes.average()
                if (!average.isFinite() || average <= 0.0) {
                    return noTrigger(rule, params, lastCandle.openTime, average, currentVolume, "baseline_non_positive")
                }
                val threshold = params.resolvedMultiplier() * average
                threshold to average
            }
            VolumeSpikeOperator.ABOVE_PCTL -> {
                val perc = percentile(historyVolumes, params.resolvedPercentile())
                if (!perc.isFinite() || perc <= 0.0) {
                    return noTrigger(rule, params, lastCandle.openTime, perc, currentVolume, "baseline_non_positive")
                }
                perc to perc
            }
        }

        val triggered = currentVolume >= threshold
        val payload = payload(
            rule = rule,
            params = params,
            ctx = ctx,
            lastCandle = lastCandle,
            lookback = lookback,
            baseline = baseline,
            threshold = threshold,
            currentVolume = currentVolume,
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            operator = params.operator,
            lookback = lookback,
            multiplier = if (params.operator == VolumeSpikeOperator.ABOVE_MA) params.resolvedMultiplier() else null,
            percentile = if (params.operator == VolumeSpikeOperator.ABOVE_PCTL) params.resolvedPercentile() else null,
            barTs = lastCandle.openTime,
        )

        val reason = if (triggered) {
            when (params.operator) {
                VolumeSpikeOperator.ABOVE_MA -> String.format(
                    Locale.US,
                    "VOLUME_SPIKE ABOVE_MA volume=%.2f threshold=%.2f",
                    currentVolume,
                    threshold,
                )
                VolumeSpikeOperator.ABOVE_PCTL -> String.format(
                    Locale.US,
                    "VOLUME_SPIKE ABOVE_PCTL volume=%.2f threshold=%.2f",
                    currentVolume,
                    threshold,
                )
            }
        } else {
            null
        }

        return RuleEvaluationResult(
            triggered = triggered,
            severity = SeverityAlert.INFO,
            fingerprint = fingerprint,
            reason = reason,
            payload = payload,
        )
    }

    private fun extractVolumes(candles: List<Candle>): List<Double>? {
        val volumes = ArrayList<Double>(candles.size)
        for (candle in candles) {
            val volume = candle.volume
            if (volume == null || !volume.isFinite() || volume < 0.0) {
                return null
            }
            volumes.add(volume)
        }
        return volumes
    }

    private fun percentile(samples: List<Double>, p: Double): Double {
        if (samples.isEmpty()) return Double.NaN
        val sorted = samples.sorted()
        val clamped = p.coerceIn(0.0, 1.0)
        val pos = clamped * (sorted.size - 1)
        val lower = pos.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val weight = pos - lower
        return if (upper == lower) {
            sorted[lower]
        } else {
            val lowVal = sorted[lower]
            val highVal = sorted[upper]
            lowVal + weight * (highVal - lowVal)
        }
    }

    private fun noTrigger(
        rule: AlertRule,
        params: VolumeSpikeParams,
        barTs: Instant?,
        baseline: Double?,
        currentVolume: Double?,
        note: String,
    ): RuleEvaluationResult {
        val payload = payload(
            rule = rule,
            params = params,
            ctx = null,
            lastCandle = null,
            lookback = params.resolvedLookback(),
            baseline = baseline,
            threshold = null,
            currentVolume = currentVolume,
            note = note,
            barTs = barTs,
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            operator = params.operator,
            lookback = params.resolvedLookback(),
            multiplier = if (params.operator == VolumeSpikeOperator.ABOVE_MA) params.resolvedMultiplier() else null,
            percentile = if (params.operator == VolumeSpikeOperator.ABOVE_PCTL) params.resolvedPercentile() else null,
            barTs = barTs,
        )

        return RuleEvaluationResult(
            triggered = false,
            severity = SeverityAlert.INFO,
            fingerprint = fingerprint,
            payload = payload,
        )
    }

    private fun payload(
        rule: AlertRule,
        params: VolumeSpikeParams,
        ctx: RuleEvaluationContext?,
        lastCandle: Candle?,
        lookback: Int,
        baseline: Double?,
        threshold: Double?,
        currentVolume: Double?,
        note: String? = null,
        barTs: Instant? = lastCandle?.openTime,
    ) = JsonNodeFactory.instance.objectNode().apply {
        put("symbol", rule.symbol)
        put("timeframe", rule.timeframe.name)
        put("operator", params.operator.name)
        put("lookback", lookback)
        putDoubleOrNull("multiplier", if (params.operator == VolumeSpikeOperator.ABOVE_MA) params.resolvedMultiplier() else null)
        putDoubleOrNull("percentile", if (params.operator == VolumeSpikeOperator.ABOVE_PCTL) params.resolvedPercentile() else null)
        putDoubleOrNull("currentVolume", currentVolume)
        putDoubleOrNull("baselineVolume", baseline)
        putDoubleOrNull("thresholdVolume", threshold)
        barTs?.let { put("barTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("barTs")
        ctx?.evaluatedAt?.let { put("evaluatedAt", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("evaluatedAt")
        note?.let { put("note", it) }
    }

    private fun fingerprint(
        kind: String,
        symbol: String,
        operator: VolumeSpikeOperator,
        lookback: Int,
        multiplier: Double?,
        percentile: Double?,
        barTs: Instant?,
    ): String {
        val canonical = buildString {
            append(kind); append('|')
            append(symbol); append('|')
            append(operator.name); append('|')
            append(lookback); append('|')
            append(multiplier?.toString() ?: ""); append('|')
            append(percentile?.toString() ?: ""); append('|')
            append(barTs?.toEpochMilli() ?: -1)
        }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun com.fasterxml.jackson.databind.node.ObjectNode.putDoubleOrNull(
        field: String,
        value: Double?,
    ) {
        if (value == null || !value.isFinite()) {
            putNull(field)
        } else {
            put(field, value)
        }
    }

    private fun Double.isFinite(): Boolean = !this.isNaN() && !this.isInfinite()
}
