package com.notivest.alertengine.pricefetcher.listener

import com.notivest.alertengine.pricefetcher.client.PriceDataClient
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class WatchlistOnRuleCreated(
    private val priceDataClient: PriceDataClient
) {
    private val logger = LoggerFactory.getLogger(WatchlistOnRuleCreated::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT /*, fallbackExecution = true*/)
    fun on(e: WatchlistAdd) = runBlocking {
        runCatching { priceDataClient.addToWatchlist(e.symbol) }
            .onFailure { ex -> logger.warn("watchlist add failed: {}", ex.message) }
    }
}