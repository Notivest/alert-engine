// src/main/kotlin/com/notivest/alertengine/ruleEvaluators/evaluators/PriceThreshold.kt
package com.notivest.alertengine.ruleEvaluators.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.PriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold.Operator
import com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold.PriceThresholdParams

import kotlin.reflect.KClass
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Evaluador puro y thread-safe para PRICE_THRESHOLD.
 * - Sin I/O, sólo cálculo en memoria
 * - Usa la última barra válida del timeframe
 * - Fingerprint estable por barra/params
 */
class PriceThreshold : RuleEvaluator<PriceThresholdParams> {

    override fun getKind(): AlertKind = AlertKind.PRICE_THRESHOLD

    override fun getParamsType(): KClass<PriceThresholdParams> = PriceThresholdParams::class

    override fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: PriceThresholdParams
    ): RuleEvaluationResult {
        // 1) Última vela disponible
        val last: Candle = prices.candles.lastOrNull()
            ?: return noTrigger(rule, null, params)

        val barTs: Instant = last.openTime
        val close: Double? = last.close

        // 2) Precio válido (no nulo, no NaN) y barra no "stale"
        if (close == null || close.isNaN() || isStale(barTs, ctx.evaluatedAt, rule.timeframe)) {
            return noTrigger(rule, barTs, params)
        }

        // 3) Comparación con BigDecimal
        val lastBD = BigDecimal.valueOf(close)
        val thr = params.value

        val triggered = when (params.operator) {
            Operator.GTE -> lastBD >= thr
            Operator.LTE -> lastBD <= thr
            Operator.GT  -> lastBD >  thr
            Operator.LT  -> lastBD <  thr
        }

        // 4) Payload y fingerprint por barra
        val payload = mapOf(
            "lastPrice" to lastBD,
            "threshold" to thr,
            "operator"  to params.operator.name,
            "delta"     to lastBD.subtract(thr),
            "symbol"    to rule.symbol,
            "timeframe" to rule.timeframe.name,
            "asOf"      to DateTimeFormatter.ISO_INSTANT.format(barTs)
        )

        val fingerprint = fingerprint(
            kind = getKind().name,
            operator = params.operator,
            threshold = thr,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            barTs = barTs
        )

        return RuleEvaluationResult(
            triggered = triggered,
            payload = payload,
            fingerprint = fingerprint,
            severity = rule.severity ?: SeverityAlert.INFO
        )
    }

    // -------------------- helpers puros --------------------

    private fun noTrigger(
        rule: AlertRule,
        barTs: Instant?,
        params: PriceThresholdParams
    ): RuleEvaluationResult {
        val payload = mapOf(
            "lastPrice" to null,
            "threshold" to params.value,
            "operator"  to params.operator.name,
            "delta"     to null,
            "symbol"    to rule.symbol,
            "timeframe" to rule.timeframe.name,
            "asOf"      to barTs?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
        )

        val fp = barTs?.let {
            fingerprint(
                kind = getKind().name,
                operator = params.operator,
                threshold = params.value,
                symbol = rule.symbol,
                timeframe = rule.timeframe,
                barTs = it
            )
        } ?: rawFingerprint(
            kind = getKind().name,
            operator = params.operator,
            threshold = params.value,
            symbol = rule.symbol,
            timeframe = rule.timeframe,
            barTsMillis = null
        )

        return RuleEvaluationResult(
            triggered = false,
            payload = payload,
            fingerprint = fp,
            severity = rule.severity ?: Severity.INFO
        )
    }

    private fun isStale(barTs: Instant, now: Instant, tf: Timeframe): Boolean {
        val window = timeframeDuration[tf] ?: return false
        // Tolerancia 2x para atrasos de ingesta
        return Duration.between(barTs, now) > window.multipliedBy(2)
    }

    private fun fingerprint(
        kind: String,
        operator: Operator,
        threshold: BigDecimal,
        symbol: String,
        timeframe: Timeframe,
        barTs: Instant
    ): String = rawFingerprint(
        kind = kind,
        operator = operator,
        threshold = threshold,
        symbol = symbol,
        timeframe = timeframe,
        barTsMillis = barTs.toEpochMilli()
    )

    private fun rawFingerprint(
        kind: String,
        operator: Operator,
        threshold: BigDecimal,
        symbol: String,
        timeframe: Timeframe,
        barTsMillis: Long?
    ): String {
        val canonical = buildString {
            append(kind); append('|')
            append(operator.name); append('|')
            append(threshold.toPlainString()); append('|')
            append(symbol); append('|')
            append(timeframe.name); append('|')
            append(barTsMillis?.toString() ?: "no-bar")
        }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val timeframeDuration: Map<Timeframe, Duration> = mapOf(
            Timeframe.M1  to Duration.ofMinutes(1),
            Timeframe.M5  to Duration.ofMinutes(5),
            Timeframe.M15 to Duration.ofMinutes(15),
            Timeframe.H1  to Duration.ofHours(1),
            Timeframe.D1  to Duration.ofDays(1)
        )
    }
}
