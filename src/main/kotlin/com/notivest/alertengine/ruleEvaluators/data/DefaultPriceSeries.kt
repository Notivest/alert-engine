package com.notivest.alertengine.ruleEvaluators.data

import com.notivest.alertengine.models.enums.Timeframe
import java.time.Instant

data class DefaultPriceSeries(
    override val symbol: String,
    override val timeframe: Timeframe,
    override val candles: List<Candle>,
    override val currentPrice: Double? = null,
    override val currentPriceAsOf: Instant? = null,
) : PriceSeries
