package com.notivest.alertengine.pricefetcher.dto.fetcher


data class CandleSeriesFetcherDto(
    val symbol: String,
    val timeframe: String,
    val items: List<CandleFetcherDto>
)