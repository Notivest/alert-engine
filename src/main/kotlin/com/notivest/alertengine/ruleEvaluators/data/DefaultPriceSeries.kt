package com.notivest.alertengine.ruleEvaluators.data

import com.notivest.alertengine.models.enums.Timeframe

data class DefaultPriceSeries(
    override val symbol: String,
    override val timeframe: Timeframe,
    override val candles: List<Candle>
) : PriceSeries