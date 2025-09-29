package com.notivest.alertengine.pricefetcher.dto.fetcher

import com.notivest.alertengine.pricefetcher.dto.QuoteDTO
import java.math.BigDecimal
import java.time.Instant

data class QuoteFetcherDto(
    val symbol: String,
    val last: BigDecimal,
    val ts: Instant,
    val open: BigDecimal?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val prevClose: BigDecimal?,
    val currency: String?,
    val source: String?,
    val stale: Boolean?
) {
    fun toCanonical() = QuoteDTO(
        symbol = symbol,
        last = last,
        asOf = ts,
        open = open,
        high = high,
        low = low,
        prevClose = prevClose,
        currency = currency,
        source = source,
        stale = stale
    )
}
