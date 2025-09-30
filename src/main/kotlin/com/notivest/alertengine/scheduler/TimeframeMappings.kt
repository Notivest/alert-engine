package com.notivest.alertengine.scheduler

import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.pricefetcher.dto.Timeframe as PriceTimeframe

val SUPPORTED_TIMEFRAMES: Map<Timeframe, PriceTimeframe> = mapOf(
    Timeframe.M1 to PriceTimeframe.M1,
    Timeframe.M5 to PriceTimeframe.M5,
    Timeframe.M15 to PriceTimeframe.M15,
    Timeframe.H1 to PriceTimeframe.H1,
    Timeframe.D1 to PriceTimeframe.D1,
)
