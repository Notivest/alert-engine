package com.notivest.alertengine.ruleEvaluators.evaluators.pctchange

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold.Operator

enum class PctChangeBasis {
    CLOSE, HL2, HLC3;

    fun valueOf(candle: Candle): Double = when (this) {
        CLOSE -> candle.close
        HL2 -> (candle.high + candle.low) / 2.0
        HLC3 -> (candle.high + candle.low + candle.close) / 3.0
    }
}

data class PctChangeParams @JsonCreator constructor(
    @JsonProperty("operator") val operator: Operator,
    @JsonProperty("pct") val pct: Double,
    @JsonProperty("lookbackBars") val lookbackBars: Int,
    @JsonProperty("basis") val basis: PctChangeBasis? = null,
) {
    init {
        require(lookbackBars >= 1) { "lookbackBars must be >= 1" }
    }

    fun resolvedBasis(): PctChangeBasis = basis ?: PctChangeBasis.CLOSE
}