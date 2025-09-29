package com.notivest.alertengine.pricefetcher.client

import com.notivest.alertengine.pricefetcher.dto.CandleDTO
import com.notivest.alertengine.pricefetcher.dto.QuoteDTO
import com.notivest.alertengine.pricefetcher.dto.Timeframe
import java.time.Instant

interface PriceDataClient {
    suspend fun getQuotes(symbols: List<String>): Map<String, QuoteDTO>
    suspend fun getHistorical(
        symbol: String,
        timeframe: Timeframe,
        from: Instant? = null,
        to: Instant? = null,
        limit: Int? = null
    ): List<CandleDTO>
}
