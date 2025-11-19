package com.notivest.alertengine.ruleEvaluators.evaluators.macross

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
class MaCrossEvaluator : RuleEvaluator<MaCrossParams> {

    override fun getKind(): AlertKind = AlertKind.MA_CROSS

    override fun getParamsType(): KClass<MaCrossParams> = MaCrossParams::class

    override fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: MaCrossParams
    ): RuleEvaluationResult {
        val candles = prices.candles
        if (candles.size < params.requiredCandles()) {
            val last = candles.lastOrNull()
            return noTrigger(
                rule = rule,
                params = params,
                barTs = last?.openTime,
                fastNow = null,
                slowNow = null,
                fastPrev = null,
                slowPrev = null,
            )
        }

        val fastNow = sma(candles, params.fast, candles.size)
        val slowNow = sma(candles, params.slow, candles.size)
        val fastPrev = sma(candles, params.fast, candles.size - 1)
        val slowPrev = sma(candles, params.slow, candles.size - 1)

        if (fastNow == null || slowNow == null || fastPrev == null || slowPrev == null) {
            val last = candles.last()
            return noTrigger(
                rule = rule,
                params = params,
                barTs = last.openTime,
                fastNow = fastNow,
                slowNow = slowNow,
                fastPrev = fastPrev,
                slowPrev = slowPrev,
            )
        }

        val lastBar = candles.last()
        val triggered = when (params.direction) {
            MaCrossDirection.UP -> fastPrev <= slowPrev && fastNow > slowNow
            MaCrossDirection.DOWN -> fastPrev >= slowPrev && fastNow < slowNow
        }

        val payload = JsonNodeFactory.instance.objectNode().apply {
            put("symbol", rule.symbol)
            put("timeframe", rule.timeframe.name)
            put("direction", params.direction.name)
            put("fastPeriod", params.fast)
            put("slowPeriod", params.slow)
            put("fastMa", fastNow)
            put("slowMa", slowNow)
            put("previousFastMa", fastPrev)
            put("previousSlowMa", slowPrev)
            put("close", lastBar.close)
            put("barTs", DateTimeFormatter.ISO_INSTANT.format(lastBar.openTime))
            put("evaluatedAt", DateTimeFormatter.ISO_INSTANT.format(ctx.evaluatedAt))
        }

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            direction = params.direction,
            fast = params.fast,
            slow = params.slow,
            barTs = lastBar.openTime,
        )

        val reason = if (triggered) {
            String.format(
                Locale.US,
                "MA_CROSS %s fast=%.4f slow=%.4f",
                params.direction.name,
                fastNow,
                slowNow,
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

    private fun noTrigger(
        rule: AlertRule,
        params: MaCrossParams,
        barTs: Instant?,
        fastNow: Double?,
        slowNow: Double?,
        fastPrev: Double?,
        slowPrev: Double?,
    ): RuleEvaluationResult {
        val payload = JsonNodeFactory.instance.objectNode().apply {
            put("symbol", rule.symbol)
            put("timeframe", rule.timeframe.name)
            put("direction", params.direction.name)
            put("fastPeriod", params.fast)
            put("slowPeriod", params.slow)
            putDoubleOrNull("fastMa", fastNow)
            putDoubleOrNull("slowMa", slowNow)
            putDoubleOrNull("previousFastMa", fastPrev)
            putDoubleOrNull("previousSlowMa", slowPrev)
            barTs?.let { put("barTs", DateTimeFormatter.ISO_INSTANT.format(it)) } ?: putNull("barTs")
        }

        val fingerprint = fingerprint(
            kind = getKind().name,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            direction = params.direction,
            fast = params.fast,
            slow = params.slow,
            barTs = barTs,
        )

        return RuleEvaluationResult(
            triggered = false,
            severity = SeverityAlert.INFO,
            fingerprint = fingerprint,
            payload = payload,
        )
    }

    private fun sma(candles: List<Candle>, length: Int, endExclusive: Int): Double? {
        if (length <= 0 || endExclusive > candles.size) return null
        val startIdx = endExclusive - length
        if (startIdx < 0) return null

        var sum = 0.0
        for (idx in startIdx until endExclusive) {
            val close = candles[idx].close
            if (!close.isFinite()) {
                return null
            }
            sum += close
        }
        return sum / length
    }

    private fun fingerprint(
        kind: String,
        symbol: String,
        timeframe: Timeframe,
        direction: MaCrossDirection,
        fast: Int,
        slow: Int,
        barTs: Instant?,
    ): String {
        val canonical = buildString {
            append(kind); append('|')
            append(symbol); append('|')
            append(timeframe.name); append('|')
            append(direction.name); append('|')
            append(fast); append('|')
            append(slow); append('|')
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
