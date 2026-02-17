package com.notivest.alertengine.ruleEvaluators.data

import com.notivest.alertengine.models.enums.Timeframe
import java.time.Instant

interface PriceSeries {
    val symbol: String
    val timeframe: Timeframe
    val candles: List<Candle>           // ordenadas por tiempo (asc)
    val currentPrice: Double?
        get() = null
    val currentPriceAsOf: Instant?
        get() = null

    fun isEmpty() = candles.isEmpty()
    fun last() = candles.last()
    fun lastClose() = candles.last().close
}
