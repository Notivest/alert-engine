package com.notivest.alertengine.scheduler

import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.ruleEvaluators.data.Candle
import com.notivest.alertengine.ruleEvaluators.data.PriceSeries

data class DefaultPriceSeries(
    override val symbol: String,
    override val timeframe: Timeframe,
    override val candles: List<Candle>
) : PriceSeries

