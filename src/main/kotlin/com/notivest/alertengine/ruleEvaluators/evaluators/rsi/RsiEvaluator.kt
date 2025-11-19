package com.notivest.alertengine.ruleEvaluators.evaluators.rsi

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
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
class RsiEvaluator : RuleEvaluator<RsiParams> {

    override fun getKind(): AlertKind = AlertKind.RSI

    override fun getParamsType(): KClass<RsiParams> = RsiParams::class

    override fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: RsiParams,
    ): RuleEvaluationResult {
        val effectivePeriod = params.resolvedPeriod()
        val expectedTimeframe = params.resolvedTimeframe(rule.timeframe)

        if (prices.candles.isEmpty()) {
            return noTrigger(
                rule = rule,
                params = params,
                timeframe = expectedTimeframe,
                barTs = null,
                currentRsi = null,
                previousRsi = null,
                message = "price_series_empty",
            )
        }

        if (prices.timeframe != expectedTimeframe) {
            val last = prices.candles.last()
            return noTrigger(
                rule = rule,
                params = params,
                timeframe = expectedTimeframe,
                barTs = last.openTime,
                currentRsi = null,
                previousRsi = null,
                message = "timeframe_mismatch",
            )
        }

        val candles = prices.candles
        if (candles.size < params.requiredCandles()) {
            val last = candles.last()
            return noTrigger(
                rule = rule,
                params = params,
                timeframe = expectedTimeframe,
                barTs = last.openTime,
                currentRsi = null,
                previousRsi = null,
                message = "insufficient_candles",
            )
        }

        val lastBar = candles.last()
        val (previousRsi, currentRsi) = computeRsiPair(candles, effectivePeriod, candles.size)
            ?: return noTrigger(
                rule = rule,
                params = params,
                timeframe = expectedTimeframe,
                barTs = lastBar.openTime,
                currentRsi = null,
                previousRsi = null,
                message = "indicator_unavailable",
            )

        if (!currentRsi.isFinite() || (params.operator.requiresPrevious() && !previousRsi.isFinite())) {
            return noTrigger(
                rule = rule,
                params = params,
                timeframe = expectedTimeframe,
                barTs = lastBar.openTime,
                currentRsi = currentRsi,
                previousRsi = previousRsi,
                message = "indicator_unavailable",
            )
        }

        val triggered = evaluateCondition(
            operator = params.operator,
            current = currentRsi,
            previous = previousRsi,
            threshold = params.threshold,
        )

        val payload = payloadNode(
            rule = rule,
            params = params,
            timeframe = expectedTimeframe,
            barTs = lastBar.openTime,
            currentRsi = currentRsi,
            previousRsi = previousRsi,
            evaluatedAt = ctx.evaluatedAt,
            note = null,
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = expectedTimeframe,
            operator = params.operator,
            threshold = params.threshold,
            period = effectivePeriod,
            barTs = lastBar.openTime,
        )

        val reason = if (triggered) {
            formatReason(
                operator = params.operator,
                current = currentRsi,
                previous = previousRsi,
                threshold = params.threshold,
            )
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

    private fun evaluateCondition(
        operator: RsiOperator,
        current: Double,
        previous: Double?,
        threshold: Double,
    ): Boolean = when (operator) {
        RsiOperator.ABOVE -> current > threshold
        RsiOperator.BELOW -> current < threshold
        RsiOperator.CROSSING_UP -> previous != null && previous <= threshold && current > threshold
        RsiOperator.CROSSING_DOWN -> previous != null && previous >= threshold && current < threshold
    }

    private fun computeRsiPair(
        candles: List<Candle>,
        period: Int,
        endExclusive: Int,
    ): Pair<Double, Double>? {
        if (period <= 0 || endExclusive > candles.size) return null
        val warmup = RsiParams.WARMUP_BARS
        val requiredCandles = period + warmup + 1
        if (endExclusive < requiredCandles) return null

        val startIdx = endExclusive - requiredCandles
        val initStart = startIdx + 1
        val initEnd = startIdx + period
        if (initStart <= 0) return null

        var gainSum = 0.0
        var lossSum = 0.0
        for (idx in initStart..initEnd) {
            val currentClose = candles[idx].close
            val previousClose = candles[idx - 1].close
            if (!currentClose.isFinite() || !previousClose.isFinite()) return null
            val change = currentClose - previousClose
            when {
                change > 0 -> gainSum += change
                change < 0 -> lossSum += -change
            }
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        if (!avgGain.isFinite() || !avgLoss.isFinite()) return null

        var prevRsi: Double? = null
        var currRsi: Double? = rsiValue(avgGain, avgLoss)
        if (currRsi == null) return null

        for (idx in (initEnd + 1) until endExclusive) {
            val currentClose = candles[idx].close
            val previousClose = candles[idx - 1].close
            if (!currentClose.isFinite() || !previousClose.isFinite()) return null

            val change = currentClose - previousClose
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            if (!avgGain.isFinite() || !avgLoss.isFinite()) return null

            val nextRsi = rsiValue(avgGain, avgLoss) ?: return null
            prevRsi = currRsi
            currRsi = nextRsi
        }

        if (prevRsi == null || currRsi == null) return null
        return prevRsi to currRsi
    }

    private fun rsiValue(avgGain: Double, avgLoss: Double): Double? {
        if (!avgGain.isFinite() || !avgLoss.isFinite()) return null
        return when {
            avgLoss == 0.0 && avgGain == 0.0 -> 50.0
            avgLoss == 0.0 -> 100.0
            avgGain == 0.0 -> 0.0
            else -> {
                val rs = avgGain / avgLoss
                if (!rs.isFinite()) null else 100.0 - (100.0 / (1.0 + rs))
            }
        }
    }

    private fun noTrigger(
        rule: AlertRule,
        params: RsiParams,
        timeframe: Timeframe,
        barTs: Instant?,
        currentRsi: Double?,
        previousRsi: Double?,
        message: String,
    ): RuleEvaluationResult {
        val payload = payloadNode(
            rule = rule,
            params = params,
            timeframe = timeframe,
            barTs = barTs,
            currentRsi = currentRsi,
            previousRsi = previousRsi,
            evaluatedAt = null,
            note = message,
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = timeframe,
            operator = params.operator,
            threshold = params.threshold,
            period = params.resolvedPeriod(),
            barTs = barTs,
        )

        return RuleEvaluationResult(
            triggered = false,
            severity = SeverityAlert.INFO,
            fingerprint = fingerprint,
            payload = payload,
        )
    }

    private fun payloadNode(
        rule: AlertRule,
        params: RsiParams,
        timeframe: Timeframe,
        barTs: Instant?,
        currentRsi: Double?,
        previousRsi: Double?,
        evaluatedAt: Instant?,
        note: String?,
    ) = JsonNodeFactory.instance.objectNode().apply {
        put("symbol", rule.symbol)
        put("timeframe", timeframe.name)
        put("operator", params.operator.name)
        put("threshold", params.threshold)
        put("period", params.resolvedPeriod())
        putDoubleOrNull("rsi", currentRsi)
        putDoubleOrNull("previousRsi", previousRsi)
        barTs?.let { put("barTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("barTs")
        evaluatedAt?.let { put("evaluatedAt", DateTimeFormatter.ISO_INSTANT.format(it)) }
            ?: putNull("evaluatedAt")
        note?.let { put("note", it) }
    }

    private fun fingerprint(
        kind: String,
        symbol: String,
        timeframe: Timeframe,
        operator: RsiOperator,
        threshold: Double,
        period: Int,
        barTs: Instant?,
    ): String {
        val canonical = buildString {
            append(kind); append('|')
            append(symbol); append('|')
            append(timeframe.name); append('|')
            append(operator.name); append('|')
            append(threshold.toString()); append('|')
            append(period); append('|')
            append(barTs?.toEpochMilli() ?: -1)
        }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun formatReason(
        operator: RsiOperator,
        current: Double,
        previous: Double?,
        threshold: Double,
    ): String = when (operator) {
        RsiOperator.ABOVE -> String.format(Locale.US, "RSI %.2f ABOVE %.2f", current, threshold)
        RsiOperator.BELOW -> String.format(Locale.US, "RSI %.2f BELOW %.2f", current, threshold)
        RsiOperator.CROSSING_UP -> String.format(
            Locale.US,
            "RSI %.2f->%.2f CROSSING_UP %.2f",
            previous ?: Double.NaN,
            current,
            threshold,
        )
        RsiOperator.CROSSING_DOWN -> String.format(
            Locale.US,
            "RSI %.2f->%.2f CROSSING_DOWN %.2f",
            previous ?: Double.NaN,
            current,
            threshold,
        )
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
