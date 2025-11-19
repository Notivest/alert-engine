package com.notivest.alertengine.pricefetcher.dto.fetcher

data class SymbolIdFetcherDto(
    val exchange: String? = null,
    val ticker: String
) {
    override fun toString(): String {
        return if (exchange.isNullOrBlank()) ticker else "$ticker.$exchange"
    }

    companion object {
        fun parse(raw: String): SymbolIdFetcherDto {
            val parts = raw.split(".")
            return when (parts.size) {
                1 -> SymbolIdFetcherDto(ticker = parts[0])
                else -> SymbolIdFetcherDto(exchange = parts.last(), ticker = parts.dropLast(1).joinToString("."))
            }
        }
    }
}
