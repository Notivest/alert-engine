package com.notivest.alertengine.pricefetcher.dto


enum class Timeframe { M1, M5, M15, H1, D1 }

private object TfMapper {
    private val toFetcher = mapOf(
        Timeframe.M1 to "T1M",
        Timeframe.M5 to "T5M",
        Timeframe.M15 to "T15M",
        Timeframe.H1 to "T1H",
        Timeframe.D1 to "T1D",
    )
    fun toFetcher(tf: Timeframe) = toFetcher[tf]!!
}