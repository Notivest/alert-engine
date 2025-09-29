package com.notivest.alertengine.pricefetcher.dto.fetcher

import com.notivest.alertengine.pricefetcher.dto.CandleDTO
import java.math.BigDecimal
import java.time.Instant

data class CandleFetcherDto(
    val ts: Instant,
    val o: BigDecimal,
    val h: BigDecimal,
    val l: BigDecimal,
    val c: BigDecimal,
    val v: Long,
    val adjusted: Boolean
) {
    fun toCanonical() = CandleDTO(
        ts = ts,
        open = o,
        high = h,
        low = l,
        close = c,
        volume = v.toBigDecimal()
    )
}