package com.notivest.alertengine.scheduler

import com.notivest.alertengine.models.enums.Timeframe
import com.notivest.alertengine.pricefetcher.client.PriceDataClient
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
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

    suspend fun loadSeries(
        cycleId: UUID,
        symbol: String,
        timeframe: Timeframe,
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

        val timerSample = Timer.start(meterRegistry)
        return try {
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

            if (ordered.isEmpty()) {
                logger.warn(
                    "alert-eval-group-empty cycleId={} symbol={} timeframe={} message=\"no candles returned\"",
                    cycleId,
                    symbol,
                    timeframe,
                )
                errorMetrics.increment("empty_series")
                null
            } else {
                DefaultPriceSeries(symbol = symbol, timeframe = timeframe, candles = ordered)
            }
        } catch (ex: Exception) {
            logger.error(
                "alert-eval-group-fetch-error cycleId={} symbol={} timeframe={} message=\"price fetch failed\"",
                cycleId,
                symbol,
                timeframe,
                ex,
            )
            errorMetrics.increment("price_fetch")
            null
        } finally {
            timerSample.stop(fetchTimer)
        }
    }
}
