package com.notivest.alertengine.ruleEvaluators.evaluators.drawdown

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class DrawdownParams @JsonCreator constructor(
    @JsonProperty("threshold") val threshold: Double,
    @JsonProperty("lookback") val lookback: Int,
) {
    init {
        require(threshold.isFinite() && threshold > 0.0) { "threshold must be > 0" }
        require(threshold <= 100.0) { "threshold must be <= 100" }
        require(lookback >= MIN_LOOKBACK) { "lookback must be >= $MIN_LOOKBACK" }
        require(lookback <= MAX_LOOKBACK) { "lookback must be <= $MAX_LOOKBACK" }
    }

    companion object {
        private const val MIN_LOOKBACK = 1
        private const val MAX_LOOKBACK = 250
    }
}

private fun Double.isFinite(): Boolean = !this.isNaN() && !this.isInfinite()
