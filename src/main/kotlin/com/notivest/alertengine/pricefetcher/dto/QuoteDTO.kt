package com.notivest.alertengine.pricefetcher.dto

import java.math.BigDecimal
import java.time.Instant


data class QuoteDTO(
    val symbol: String,
    val last: BigDecimal,
    val asOf: Instant,
    val open: BigDecimal? = null,
    val high: BigDecimal? = null,
    val low: BigDecimal? = null,
    val prevClose: BigDecimal? = null,
    val currency: String? = null,
    val source: String? = null,
    val stale: Boolean? = null
)
