package com.notivest.alertengine.scheduler

import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.pricefetcher.client.PriceDataClient
import com.notivest.alertengine.pricefetcher.dto.CandleMapper
import com.notivest.alertengine.ruleEvaluators.data.DefaultPriceSeries
import com.notivest.alertengine.ruleEvaluators.data.Candle
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Component
class GroupProcessor(
    private val priceDataClient: PriceDataClient,
    private val properties: AlertEvaluationSchedulerProperties,
    private val meterRegistry: MeterRegistry,
    private val errorMetrics: ErrorMetrics,
) {

    private val logger = LoggerFactory.getLogger(GroupProcessor::class.java)
    private val fetchTimer = Timer.builder("alertengine.scheduler.fetch.latency")
        .description("Latency of price fetch operations per group")
        .register(meterRegistry)

    suspend fun loadHistoricalSeries(
        cycleId: UUID,
        symbol: String,
        timeframe: Timeframe,
        evaluatedAt: Instant,
    ): DefaultPriceSeries? = loadSeries(cycleId, symbol, timeframe, PriceDataSource.HISTORICAL, evaluatedAt)

    suspend fun loadQuoteSeries(
        cycleId: UUID,
        symbol: String,
        timeframe: Timeframe,
    ): DefaultPriceSeries? = loadSeries(cycleId, symbol, timeframe, PriceDataSource.QUOTES)

    private suspend fun loadSeries(
        cycleId: UUID,
        symbol: String,
        timeframe: Timeframe,
        source: PriceDataSource,
        evaluatedAt: Instant? = null,
    ): DefaultPriceSeries? {
        val timerSample = Timer.start(meterRegistry)
        return try {
            when (source) {
                PriceDataSource.HISTORICAL -> loadFromHistorical(cycleId, symbol, timeframe, evaluatedAt ?: Instant.now())
                PriceDataSource.QUOTES -> loadFromQuotes(cycleId, symbol, timeframe)
            }
        } catch (ex: Exception) {
            logger.error(
                "alert-eval-group-fetch-error cycleId={} symbol={} timeframe={} source={} message=\"price fetch failed\"",
                cycleId,
                symbol,
                timeframe,
                source,
                ex,
            )
            errorMetrics.increment("price_fetch")
            null
        } finally {
            timerSample.stop(fetchTimer)
        }
    }

    private suspend fun loadFromQuotes(
        cycleId: UUID,
        symbol: String,
        timeframe: Timeframe,
    ): DefaultPriceSeries? {
        val quotes = withContext(Dispatchers.IO) {
            priceDataClient.getQuotes(listOf(symbol))
        }
        val quote = quotes[symbol]
        if (quote == null) {
            logger.warn(
                "alert-eval-group-empty-quote cycleId={} symbol={} message=\"quote not returned\"",
                cycleId,
                symbol,
            )
            errorMetrics.increment("quote_missing")
            return null
        }

        if (quote.stale == true) {
            logger.warn(
                "alert-eval-group-stale-quote cycleId={} symbol={} message=\"quote marked as stale\"",
                cycleId,
                symbol,
            )
            errorMetrics.increment("quote_stale")
            return null
        }

        val referencePrice = quote.last ?: quote.prevClose
        if (referencePrice == null) {
            logger.warn(
                "alert-eval-group-empty-quote-price cycleId={} symbol={} message=\"quote has no last/prevClose\"",
                cycleId,
                symbol,
            )
            errorMetrics.increment("quote_missing")
            return null
        }

        val lastPrice = referencePrice.toDouble()
        val candle = Candle(
            openTime = quote.asOf,
            open = quote.open?.toDouble() ?: lastPrice,
            high = quote.high?.toDouble() ?: lastPrice,
            low = quote.low?.toDouble() ?: lastPrice,
            close = lastPrice,
        )

        return DefaultPriceSeries(
            symbol = symbol,
            timeframe = timeframe,
            candles = listOf(candle),
            currentPrice = lastPrice,
            currentPriceAsOf = quote.asOf,
        )
    }

    private suspend fun loadFromHistorical(
        cycleId: UUID,
        symbol: String,
        timeframe: Timeframe,
        evaluatedAt: Instant,
    ): DefaultPriceSeries? {
        val priceTimeframe = SUPPORTED_TIMEFRAMES[timeframe]
        if (priceTimeframe == null) {
            logger.warn(
                "alert-eval-group-unsupported cycleId={} symbol={} timeframe={} message=\"timeframe not supported by price service\"",
                cycleId,
                symbol,
                timeframe,
            )
            errorMetrics.increment("unsupported_timeframe")
            return null
        }

        val candles = withContext(Dispatchers.IO) {
            priceDataClient.getHistorical(
                symbol = symbol,
                timeframe = priceTimeframe,
                limit = properties.historyLookback,
            )
        }

        val ordered = candles
            .map(CandleMapper::fromDto)
            .sortedBy { it.openTime }
        val closedOnly = filterClosedBars(ordered, timeframe, evaluatedAt)

        if (closedOnly.isEmpty()) {
            logger.warn(
                "alert-eval-group-empty cycleId={} symbol={} timeframe={} message=\"no closed candles returned\"",
                cycleId,
                symbol,
                timeframe,
            )
            errorMetrics.increment("empty_series")
            return null
        }

        return DefaultPriceSeries(symbol = symbol, timeframe = timeframe, candles = closedOnly)
    }

    private fun filterClosedBars(
        candles: List<Candle>,
        timeframe: Timeframe,
        evaluatedAt: Instant,
    ): List<Candle> {
        if (timeframe != Timeframe.D1) {
            return candles
        }

        val utcDayStart = evaluatedAt.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
        return candles.filter { it.openTime < utcDayStart }
    }

    enum class PriceDataSource {
        HISTORICAL,
        QUOTES,
    }
}
