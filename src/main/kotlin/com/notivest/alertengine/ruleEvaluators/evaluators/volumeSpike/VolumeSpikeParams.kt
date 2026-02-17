package com.notivest.alertengine.ruleEvaluators.evaluators.volumeSpike

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

enum class VolumeSpikeOperator {
    ABOVE_MA,
    ABOVE_PCTL,
}

data class VolumeSpikeParams @JsonCreator constructor(
    @JsonProperty("lookback") private val lookbackRaw: Int? = null,
    @JsonProperty("multiplier") private val multiplierRaw: Double? = null,
    @JsonProperty("percentile") private val percentileRaw: Double? = null,
    @JsonProperty("operator") val operator: VolumeSpikeOperator,
) {
    init {
        val lookback = resolvedLookback()
        require(lookback >= MIN_LOOKBACK) { "lookback must be >= $MIN_LOOKBACK" }
        require(lookback <= MAX_LOOKBACK) { "lookback must be <= $MAX_LOOKBACK" }
        val multiplier = resolvedMultiplier()
        require(multiplier.isFinite() && multiplier > 0.0) { "multiplier must be > 0" }
        require(multiplier <= MAX_MULTIPLIER) { "multiplier must be <= $MAX_MULTIPLIER" }
        if (operator == VolumeSpikeOperator.ABOVE_PCTL) {
            val percentile = resolvedPercentile()
            require(percentile.isFinite() && percentile > 0.0 && percentile < 1.0) {
                "percentile must be between 0 and 1"
            }
        }
    }

    fun resolvedLookback(): Int = lookbackRaw ?: DEFAULT_LOOKBACK

    fun resolvedMultiplier(): Double = multiplierRaw ?: DEFAULT_MULTIPLIER

    fun resolvedPercentile(): Double = percentileRaw ?: DEFAULT_PERCENTILE

    fun requiredCandles(): Int = resolvedLookback() + 1

    companion object {
        private const val MIN_LOOKBACK = 2
        private const val MAX_LOOKBACK = 250
        private const val DEFAULT_LOOKBACK = 20
        private const val MAX_MULTIPLIER = 100.0
        private const val DEFAULT_MULTIPLIER = 2.0
        private const val DEFAULT_PERCENTILE = 0.95
    }
}

private fun Double.isFinite(): Boolean = !this.isNaN() && !this.isInfinite()
