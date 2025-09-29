package com.notivest.alertengine.pricefetcher.dto

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