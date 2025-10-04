package com.notivest.alertengine.ruleEvaluators.evaluators.priceThreshold

import java.math.BigDecimal

data class PriceThresholdParams(
    val operator: Operator,
    val value: BigDecimal
)