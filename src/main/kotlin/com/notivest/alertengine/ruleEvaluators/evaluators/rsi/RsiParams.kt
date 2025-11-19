package com.notivest.alertengine.ruleEvaluators.evaluators.rsi

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.notivest.alertengine.models.enums.Timeframe

enum class RsiOperator {
    ABOVE,
    BELOW,
    CROSSING_UP,
    CROSSING_DOWN;

    fun requiresPrevious(): Boolean = this == CROSSING_UP || this == CROSSING_DOWN
}

data class RsiParams @JsonCreator constructor(
    @JsonProperty("period") val period: Int? = null,
    @JsonProperty("threshold") val threshold: Double,
    @JsonProperty("operator") val operator: RsiOperator,
    @JsonProperty("timeframe") val timeframe: Timeframe? = null,
) {
    init {
        require(resolvedPeriod() >= 1) { "period must be >= 1" }
        require(threshold.isFinite()) { "threshold must be finite" }
        require(threshold in MIN_THRESHOLD..MAX_THRESHOLD) { "threshold must be between 0 and 100" }
    }

    fun resolvedPeriod(): Int = period ?: DEFAULT_PERIOD

    fun resolvedTimeframe(ruleTimeframe: Timeframe): Timeframe = timeframe ?: ruleTimeframe

    fun requiredCandles(): Int = resolvedPeriod() + WARMUP_BARS + 1

    companion object {
        private const val DEFAULT_PERIOD = 14
        private const val MIN_THRESHOLD = 0.0
        private const val MAX_THRESHOLD = 100.0
        const val WARMUP_BARS = 100
    }
}

private fun Double.isFinite(): Boolean = !this.isNaN() && !this.isInfinite()
