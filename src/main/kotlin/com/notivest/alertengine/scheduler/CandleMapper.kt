package com.notivest.alertengine.scheduler

import com.notivest.alertengine.pricefetcher.dto.CandleDTO
import com.notivest.alertengine.ruleEvaluators.data.Candle

object CandleMapper {
    fun fromDto(dto: CandleDTO): Candle = Candle(
        openTime = dto.ts,
        open = dto.open.toDouble(),
        high = dto.high.toDouble(),
        low = dto.low.toDouble(),
        close = dto.close.toDouble(),
        volume = dto.volume?.toDouble(),
    )
}
