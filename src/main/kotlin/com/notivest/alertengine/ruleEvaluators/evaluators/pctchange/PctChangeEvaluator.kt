package com.notivest.alertengine.ruleEvaluators.evaluators.pctchange

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
import com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold.Operator
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Component
class PctChangeEvaluator : RuleEvaluator<PctChangeParams> {

    override fun getKind(): AlertKind = AlertKind.PCT_CHANGE

    override fun getParamsType() = PctChangeParams::class

    override fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: PctChangeParams,
    ): RuleEvaluationResult {
        if (prices.candles.size < params.lookbackBars + 1) {
            return noTrigger(rule, params, null, null)
        }

        val basis = params.resolvedBasis()
        val latest: Candle = prices.candles.last()
        val comparisonIdx = prices.candles.size - 1 - params.lookbackBars
        val past: Candle = prices.candles[comparisonIdx]

        val latestValue = basis.valueOf(latest)
        val pastValue = basis.valueOf(past)

        if (!latestValue.isFinite() || !pastValue.isFinite()) {
            return noTrigger(rule, params, latest.openTime, past.openTime)
        }

        if (abs(pastValue) < MIN_DENOMINATOR) {
            return noTrigger(rule, params, latest.openTime, past.openTime)
        }

        val pctChange = ((latestValue - pastValue) / pastValue) * 100.0
        if (!pctChange.isFinite()) {
            return noTrigger(rule, params, latest.openTime, past.openTime)
        }

        val triggered = compare(params.operator, pctChange, params.pct)
        val severity = SeverityAlert.INFO
        val payload = JsonNodeFactory.instance.objectNode().apply {
            put("symbol", rule.symbol)
            put("timeframe", rule.timeframe.name)
            put("operator", params.operator.name)
            put("thresholdPct", params.pct)
            put("actualPct", pctChange)
            put("lookbackBars", params.lookbackBars)
            put("basis", basis.name)
            put("fromTs", DateTimeFormatter.ISO_INSTANT.format(past.openTime))
            put("toTs", DateTimeFormatter.ISO_INSTANT.format(latest.openTime))
            put("evaluatedAt", DateTimeFormatter.ISO_INSTANT.format(ctx.evaluatedAt))
        }

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            operator = params.operator,
            threshold = params.pct,
            lookback = params.lookbackBars,
            basis = basis,
            fromTs = past.openTime,
            toTs = latest.openTime,
        )

        val reason = if (triggered) {
            val actualFormatted = formatPct(pctChange)
            val thresholdFormatted = formatPct(params.pct)
            "PCT_CHANGE $actualFormatted ${params.operator.name} $thresholdFormatted"
        } else {
            null
        }

        return RuleEvaluationResult(
            triggered = triggered,
            severity = severity,
            fingerprint = fingerprint,
            reason = reason,
            payload = payload,
        )
    }

    private fun noTrigger(
        rule: AlertRule,
        params: PctChangeParams,
        latestTs: Instant?,
        pastTs: Instant?,
    ): RuleEvaluationResult {
        val basis = params.resolvedBasis()
        val payload = JsonNodeFactory.instance.objectNode().apply {
            put("symbol", rule.symbol)
            put("timeframe", rule.timeframe.name)
            put("operator", params.operator.name)
            put("thresholdPct", params.pct)
            putNull("actualPct")
            put("lookbackBars", params.lookbackBars)
            put("basis", basis.name)
            latestTs?.let { put("toTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("toTs")
            pastTs?.let { put("fromTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("fromTs")
        }

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            operator = params.operator,
            threshold = params.pct,
            lookback = params.lookbackBars,
            basis = basis,
            fromTs = pastTs,
            toTs = latestTs,
        )

        return RuleEvaluationResult(
            triggered = false,
            severity = SeverityAlert.INFO,
            fingerprint = fingerprint,
            payload = payload,
        )
    }

    private fun compare(operator: Operator, actual: Double, threshold: Double): Boolean = when (operator) {
        Operator.GTE -> actual >= threshold
        Operator.GT -> actual > threshold
        Operator.LTE -> actual <= threshold
        Operator.LT -> actual < threshold
    }

    private fun formatPct(value: Double): String = String.format(java.util.Locale.US, "%+.2f%%", value)

    private fun fingerprint(
        kind: String,
        symbol: String,
        timeframe: Timeframe,
        operator: Operator,
        threshold: Double,
        lookback: Int,
        basis: PctChangeBasis,
        fromTs: Instant?,
        toTs: Instant?,
    ): String {
        val canonical = buildString {
            append(kind); append('|')
            append(symbol); append('|')
            append(timeframe.name); append('|')
            append(operator.name); append('|')
            append(threshold.toString()); append('|')
            append(lookback); append('|')
            append(basis.name); append('|')
            append(fromTs?.toEpochMilli() ?: -1); append('|')
            append(toTs?.toEpochMilli() ?: -1)
        }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun Double.isFinite(): Boolean = !this.isNaN() && !this.isInfinite()

    companion object {
        private const val MIN_DENOMINATOR = 1e-9
    }
}