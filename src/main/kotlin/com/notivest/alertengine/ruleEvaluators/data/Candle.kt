package com.notivest.alertengine.ruleEvaluators.data

import java.time.Instant

data class Candle(
    val openTime: Instant,
    val open: Double, val high: Double, val low: Double, val close: Double,
    val volume: Double? = null
)