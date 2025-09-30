package com.notivest.alertengine.ruleEvaluators.data

import com.notivest.alertengine.models.enums.Timeframe

interface PriceSeries {
    val symbol: String
    val timeframe: Timeframe
    val candles: List<Candle>           // ordenadas por tiempo (asc)

    fun isEmpty() = candles.isEmpty()
    fun last() = candles.last()
    fun lastClose() = candles.last().close
}