package com.notivest.alertengine.pricefetcher.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.alertengine.pricefetcher.dto.CandleDTO
import com.notivest.alertengine.pricefetcher.dto.QuoteDTO
import com.notivest.alertengine.pricefetcher.dto.Timeframe
import com.notivest.alertengine.pricefetcher.dto.fetcher.CandleSeriesFetcherDto
import com.notivest.alertengine.pricefetcher.dto.fetcher.QuoteFetcherDto
import com.notivest.alertengine.pricefetcher.exception.PriceDataClientException
import com.notivest.alertengine.pricefetcher.config.PriceDataClientProperties
import com.notivest.alertengine.pricefetcher.tokenprovider.TokenPolicy

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

class PriceDataClientWeb(
    private val client: WebClient,
    private val props: PriceDataClientProperties,
    private val tokenPolicy: TokenPolicy,
    private val meterRegistry: MeterRegistry,
    private val mapper: ObjectMapper
) : PriceDataClient {

    override suspend fun getQuotes(symbols: List<String>): Map<String, QuoteDTO> {
        require(symbols.isNotEmpty()) { "symbols must not be empty" }
        val sym = symbols.joinToString(",")

        // 1) intento batcheado
        val batch = runCatching {
            request<List<QuoteFetcherDto>>(
                endpoint = "/quotes",
                query = mapOf("symbols" to sym),
                endpointName = "quotes"
            )
        }
        if (batch.isSuccess) {
            return batch.getOrThrow().associateBy({ it.symbol }, { it.toCanonical() })
        }

        // 2) si batch falló por 404 → fan-out por símbolo
        val cause = batch.exceptionOrNull()
        if (cause is PriceDataClientException && cause.kind == PriceDataClientException.Kind.NOT_FOUND) {
            val out = mutableMapOf<String, QuoteDTO>()
            for (s in symbols) {
                val one = runCatching {
                    request<List<QuoteFetcherDto>>(
                        endpoint = "/quotes",
                        query = mapOf("symbols" to s),
                        endpointName = "quotes"
                    )
                }
                if (one.isSuccess) {
                    one.getOrThrow().firstOrNull()?.let { out[it.symbol] = it.toCanonical() }
                } else {
                    val e = one.exceptionOrNull()
                    // ignoramos 404/400 individuales; otras categorías → propagar
                    if (e !is PriceDataClientException ||
                        (e.kind != PriceDataClientException.Kind.NOT_FOUND &&
                                e.kind != PriceDataClientException.Kind.BAD_REQUEST)
                    ) {
                        throw e ?: RuntimeException("Unknown error")
                    }
                }
            }
            return out
        }

        throw cause ?: RuntimeException("Unknown error getting quotes")
    }

    override suspend fun getHistorical(
        symbol: String,
        timeframe: Timeframe,
        from: Instant?,
        to: Instant?,
        limit: Int?
    ): List<CandleDTO> {
        val toTs = to ?: Instant.now()
        val fromTs = from ?: (limit?.let { deriveFrom(toTs, timeframe, it) }
            ?: toTs.minus(Duration.ofDays(365)))

        val raw: CandleSeriesFetcherDto = request(
            endpoint = "/historical",
            query = mapOf(
                "symbol" to symbol,
                "from" to fromTs.toString(),
                "to" to toTs.toString(),
                "tf" to TfMapper.toFetcher(timeframe),
                "adjusted" to "true"
            ),
            endpointName = "historical"
        )

        val ascNoDup = raw.items
            .asSequence()
            .map { it.toCanonical() }
            .distinctBy { it.ts }
            .sortedBy { it.ts }
            .toList()
            .let { if (limit != null) it.takeLast(limit) else it }

        return ascNoDup
    }

    override suspend fun addToWatchlist(symbol: String, enabled: Boolean, priority: Int?) {
        val token = tokenPolicy.resolveToken()
        val body = mapOf("symbol" to symbol, "enabled" to enabled, "priority" to priority)
        runCatching {
            client.post().uri("/watchlist")
                .headers { if (!token.isNullOrBlank()) it.setBearerAuth(token) }
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
        }.onFailure { ex ->
            val wcre = ex as? WebClientResponseException
            val alreadyExists =
                wcre?.statusCode?.value() == 400 && wcre.responseBodyAsString.contains("exists", ignoreCase = true)
            if (!alreadyExists) throw ex // re-lanzá solo si no es “ya existe”
        }
    }

    // ---------------- Infra común ----------------

    private suspend inline fun <reified T> request(
        endpoint: String,
        query: Map<String, String>,
        endpointName: String
    ): T {
        val token = tokenPolicy.resolveToken()

        // build URI con query params
        val uriSpec = client.get()
            .uri { b ->
                var ub = b.path(endpoint)
                query.forEach { (k, v) -> ub = ub.queryParam(k, v) }
                ub.build()
            }
            .headers { h -> if (!token.isNullOrBlank()) h.setBearerAuth(token) }
            .accept(MediaType.APPLICATION_JSON)

        val timer = Timer.start(meterRegistry)
        try {
            return withRetry(endpointName) {
                val body: String = uriSpec
                    .retrieve() // lanza WebClientResponseException en 4xx/5xx
                    .bodyToMono(String::class.java)
                    .awaitSingle()

                mapper.readValue(body, object : TypeReference<T>() {})
            }
        } catch (e: WebClientResponseException) {
            throw mapHttpError(e.statusCode.value(), e)
        } catch (e: io.netty.handler.timeout.ReadTimeoutException) {
            throw PriceDataClientException(PriceDataClientException.Kind.TIMEOUT, null, "Read timeout", e)
        } catch (e: java.net.ConnectException) {
            throw PriceDataClientException(PriceDataClientException.Kind.NETWORK, null, "Connect error", e)
        } catch (e: PriceDataClientException) {
            throw e
        } catch (e: Throwable) {
            throw PriceDataClientException(PriceDataClientException.Kind.INVALID_RESPONSE, null, "Invalid/Unexpected", e)
        } finally {
            timer.stop(
                Timer.builder("pricedata.client.latency")
                    .tag("endpoint", endpointName)
                    .register(meterRegistry)
            )
        }
    }

    private suspend fun <T> withRetry(endpoint: String, block: suspend () -> T): T {
        var attempt = 0
        var last: Throwable? = null
        while (attempt <= props.retries.max) {
            try {
                return block()
            } catch (e: PriceDataClientException) {
                if (!shouldRetry(e)) throw e
                last = e
            } catch (e: Throwable) {
                val wrap = when (e) {
                    is java.net.SocketTimeoutException ->
                        PriceDataClientException(PriceDataClientException.Kind.TIMEOUT, null, "Timeout", e)
                    is java.io.IOException ->
                        PriceDataClientException(PriceDataClientException.Kind.NETWORK, null, "Network", e)
                    else -> e
                }
                if (wrap is PriceDataClientException && shouldRetry(wrap)) last = wrap else throw wrap
            }
            attempt++
            delay(backoffDelay(attempt))
        }
        throw (last ?: RuntimeException("Unknown error on $endpoint retries"))
    }

    private fun shouldRetry(e: PriceDataClientException): Boolean =
        when (e.kind) {
            PriceDataClientException.Kind.NETWORK,
            PriceDataClientException.Kind.TIMEOUT,
            PriceDataClientException.Kind.RATE_LIMIT,
            PriceDataClientException.Kind.SERVER_ERROR -> true
            else -> false
        }

    private fun backoffDelay(attempt: Int): Long {
        val base = props.retries.baseDelayMs *
                (1L shl (attempt - 1)).coerceAtMost(props.retries.maxDelayMs / props.retries.baseDelayMs)
        val jitter = ThreadLocalRandom.current().nextLong(0, props.retries.jitterMs + 1)
        return (base + jitter).coerceAtMost(props.retries.maxDelayMs)
    }

    private fun mapHttpError(status: Int, cause: Throwable): PriceDataClientException {
        val kind = when (status) {
            400 -> PriceDataClientException.Kind.BAD_REQUEST
            404 -> PriceDataClientException.Kind.NOT_FOUND
            429 -> PriceDataClientException.Kind.RATE_LIMIT
            in 500..599 -> PriceDataClientException.Kind.SERVER_ERROR
            else -> PriceDataClientException.Kind.INVALID_RESPONSE
        }
        return PriceDataClientException(kind, status, "HTTP $status", cause)
    }

    private fun deriveFrom(to: Instant, tf: Timeframe, limit: Int): Instant {
        val unit = when (tf) {
            Timeframe.M1  -> Duration.ofMinutes(1)
            Timeframe.M5  -> Duration.ofMinutes(5)
            Timeframe.M15 -> Duration.ofMinutes(15)
            Timeframe.H1  -> Duration.ofHours(1)
            Timeframe.D1  -> Duration.ofDays(1)
        }
        return to.minus(unit.multipliedBy(limit.toLong()))
    }

    private object TfMapper {
        fun toFetcher(tf: Timeframe): String = when (tf) {
            Timeframe.M1  -> "T1M"
            Timeframe.M5  -> "T5M"
            Timeframe.M15 -> "T15M"
            Timeframe.H1  -> "T1H"
            Timeframe.D1  -> "T1D"
        }
    }
}