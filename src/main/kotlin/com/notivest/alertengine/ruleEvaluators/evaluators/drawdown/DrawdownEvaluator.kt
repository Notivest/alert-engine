package com.notivest.alertengine.ruleEvaluators.evaluators.drawdown

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
class DrawdownEvaluator : RuleEvaluator<DrawdownParams> {

    override fun getKind(): AlertKind = AlertKind.DRAWDOWN

    override fun getParamsType(): KClass<DrawdownParams> = DrawdownParams::class

    override fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: DrawdownParams,
    ): RuleEvaluationResult {
        val candles = prices.candles
        if (candles.size < params.lookback) {
            return noTrigger(rule, params, null, null, null, null, "insufficient_candles")
        }

        val window = candles.takeLast(params.lookback)
        val last = candles.last()
        val currentPrice = prices.currentPrice ?: last.close
        val currentPriceTs = prices.currentPriceAsOf ?: last.openTime
        if (!currentPrice.isFinite()) {
            return noTrigger(rule, params, currentPriceTs, null, currentPrice, null, "current_price_unavailable")
        }

        val maxPrice = window.maxOfOrNull(Candle::high)
        if (maxPrice == null || !maxPrice.isFinite() || maxPrice <= 0.0) {
            return noTrigger(rule, params, currentPriceTs, null, currentPrice, null, "max_price_unavailable")
        }

        val drawdownPct = ((maxPrice - currentPrice) / maxPrice) * 100.0
        if (!drawdownPct.isFinite()) {
            return noTrigger(rule, params, currentPriceTs, maxPrice, currentPrice, null, "drawdown_unavailable")
        }

        val triggered = drawdownPct >= params.threshold
        val fromTs = window.firstOrNull()?.openTime
        val payload = payload(
            rule = rule,
            params = params,
            evaluatedAt = ctx.evaluatedAt,
            barTs = currentPriceTs,
            fromTs = fromTs,
            maxPrice = maxPrice,
            currentPrice = currentPrice,
            drawdownPct = drawdownPct,
            note = null,
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            threshold = params.threshold,
            lookback = params.lookback,
            barTs = currentPriceTs,
        )

        val reason = if (triggered) {
            String.format(Locale.US, "DRAWDOWN %.2f%% >= %.2f%%", drawdownPct, params.threshold)
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

    private fun noTrigger(
        rule: AlertRule,
        params: DrawdownParams,
        barTs: Instant?,
        maxPrice: Double?,
        currentPrice: Double?,
        drawdownPct: Double?,
        note: String,
    ): RuleEvaluationResult {
        val payload = payload(
            rule = rule,
            params = params,
            evaluatedAt = null,
            barTs = barTs,
            fromTs = null,
            maxPrice = maxPrice,
            currentPrice = currentPrice,
            drawdownPct = drawdownPct,
            note = note,
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            threshold = params.threshold,
            lookback = params.lookback,
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
        params: DrawdownParams,
        evaluatedAt: Instant?,
        barTs: Instant?,
        fromTs: Instant?,
        maxPrice: Double?,
        currentPrice: Double?,
        drawdownPct: Double?,
        note: String?,
    ) = JsonNodeFactory.instance.objectNode().apply {
        put("symbol", rule.symbol)
        put("timeframe", rule.timeframe.name)
        put("threshold", params.threshold)
        put("lookback", params.lookback)
        putDoubleOrNull("maxPrice", maxPrice)
        putDoubleOrNull("currentPrice", currentPrice)
        putDoubleOrNull("drawdownPct", drawdownPct)
        barTs?.let { put("barTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("barTs")
        fromTs?.let { put("fromTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("fromTs")
        evaluatedAt?.let { put("evaluatedAt", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("evaluatedAt")
        note?.let { put("note", it) }
    }

    private fun fingerprint(
        kind: String,
        symbol: String,
        timeframe: Timeframe,
        threshold: Double,
        lookback: Int,
        barTs: Instant?,
    ): String {
        val canonical = buildString {
            append(kind); append('|')
            append(symbol); append('|')
            append(timeframe.name); append('|')
            append(threshold.toString()); append('|')
            append(lookback); append('|')
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
