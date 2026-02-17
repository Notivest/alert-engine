package com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold

import java.math.BigDecimal

data class PriceThresholdParams(
    val operator: Operator,
    val value: BigDecimal
) {
    init {
        require(value > BigDecimal.ZERO) { "value must be > 0" }
        require(value <= MAX_VALUE) { "value must be <= $MAX_VALUE" }
    }

    companion object {
        private val MAX_VALUE = BigDecimal("1000000")
    }
}
