package com.notivest.alertengine.pricefetcher.dto

import com.notivest.alertengine.ruleEvaluators.data.Candle
import java.math.BigDecimal
import java.time.Instant

data class CandleDTO(
    val ts: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal? = null
)

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