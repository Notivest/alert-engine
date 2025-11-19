package com.notivest.alertengine.pricefetcher

import com.notivest.alertengine.pricefetcher.client.PriceDataClient
import com.notivest.alertengine.pricefetcher.client.PriceDataClientWeb
import com.notivest.alertengine.pricefetcher.config.PriceDataClientProperties
import com.notivest.alertengine.pricefetcher.dto.Timeframe
import com.notivest.alertengine.pricefetcher.exception.PriceDataClientException
import com.notivest.alertengine.pricefetcher.tokenprovider.TokenPolicy
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.ExchangeStrategies

@OptIn(ExperimentalCoroutinesApi::class)
class PriceDataClientWebTest {

    private lateinit var server: MockWebServer
    private lateinit var client: PriceDataClient
    private lateinit var props: PriceDataClientProperties
    private val meter = SimpleMeterRegistry()
    private val tokenPolicy = mockk<TokenPolicy>(relaxed = true)
    private lateinit var mapper: ObjectMapper

    @BeforeEach
    fun setup() {
        // Nuevo server por test ⇒ no quedan requests viejas en la cola
        server = MockWebServer()
        server.start()

        val base = server.url("/").toString().removeSuffix("/")
        props = PriceDataClientProperties().apply {
            baseUrl = base
            connectTimeoutMs = 2_000
            readTimeoutMs = 5_000
            retries = PriceDataClientProperties.Retries().apply {
                max = 0           // sin reintentos para que los asserts de requests sean deterministas
                baseDelayMs = 5
                maxDelayMs = 25
                jitterMs = 0
            }
        }

        coEvery { tokenPolicy.resolveToken() } returns null

        mapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val strategies = ExchangeStrategies.builder()
            .codecs {
                it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON))
                it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON))
            }
            .build()

        val webClient = WebClient.builder()
            .baseUrl(props.baseUrl)
            .exchangeStrategies(strategies)
            .defaultHeaders { h -> h.accept = listOf(MediaType.APPLICATION_JSON) }
            .build()

        client = PriceDataClientWeb(
            client = webClient,
            props = props,
            tokenPolicy = tokenPolicy,
            meterRegistry = meter,
            mapper = mapper        // <- importante: el mismo mapper
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getQuotes - ok multi simbolo`() = runTest {
        server.enqueue(json(200, """
            [
              {"symbol":"AAPL","last":100.0,"ts":"2024-01-01T00:00:00Z","open":99.0,"high":101.0,"low":98.0,"prevClose":97.5,"currency":"USD","source":"X","stale":false},
              {"symbol":"MSFT","last":200.0,"ts":"2024-01-01T00:00:00Z","open":199.0,"high":201.0,"low":198.0,"prevClose":197.5,"currency":"USD","source":"Y","stale":false}
            ]
        """.trimIndent()))

        val res = client.getQuotes(listOf("AAPL", "MSFT"))

        assertThat(res).hasSize(2)
        assertThat(res["AAPL"]!!.last.toPlainString()).isEqualTo("100.0")
        assertThat(res["MSFT"]!!.last.toPlainString()).isEqualTo("200.0")

        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(req.path).startsWith("/quotes")
        assertThat(req.path).contains("symbols=")
        assertThat(req.path).contains("AAPL")
        assertThat(req.path).contains("MSFT")
    }

    @Test
    fun `getQuotes - batch 404 activa fan-out y tolera simbolos invalidos`() = runTest {
        // 1) Batch 404
        server.enqueue(json(404, """{"error":"not found"}"""))
        // 2) AAPL OK
        server.enqueue(json(200, """
            [
              {"symbol":"AAPL","last":101.0,"ts":"2024-01-01T00:00:00Z","open":100.0,"high":102.0,"low":99.0,"prevClose":98.0,"currency":"USD","source":"X","stale":false}
            ]
        """.trimIndent()))
        // 3) FOOBAR 404 (se ignora)
        server.enqueue(json(404, """{"error":"not found"}"""))

        val res = client.getQuotes(listOf("AAPL", "FOOBAR"))

        assertThat(res).hasSize(1)
        assertThat(res["AAPL"]!!.last.toPlainString()).isEqualTo("101.0")

        val first = server.takeRequest() // batch
        val second = server.takeRequest() // AAPL
        val third = server.takeRequest() // FOOBAR
        assertThat(first.path).startsWith("/quotes")
        assertThat(second.path).contains("symbols=AAPL")
        assertThat(third.path).contains("symbols=FOOBAR")
    }

    @Test
    fun `getHistorical - orden ascendente, dedupe y limit`() = runTest {
        server.enqueue(json(200, """
            {
              "symbol": {"ticker":"AAPL"}, "timeframe":"T1D",
              "items":[
                {"ts":"2024-01-02T00:00:00Z","o":2,"h":3,"l":1,"c":2,"v":100,"adjusted":true},
                {"ts":"2024-01-01T00:00:00Z","o":1,"h":2,"l":1,"c":1,"v":90,"adjusted":true},
                {"ts":"2024-01-01T00:00:00Z","o":1,"h":2,"l":1,"c":1,"v":90,"adjusted":true}
              ]
            }
        """.trimIndent()))

        val list = client.getHistorical(
            symbol = "AAPL",
            timeframe = Timeframe.D1,
            from = Instant.parse("2023-12-31T00:00:00Z"),
            to = Instant.parse("2024-01-03T00:00:00Z"),
            limit = 1
        )

        assertThat(list).hasSize(1)
        assertThat(list[0].ts.toString()).isEqualTo("2024-01-02T00:00:00Z")

        val req = server.takeRequest()
        assertThat(req.path).contains("/historical")
        assertThat(req.path).contains("tf=T1D")
        assertThat(req.path).contains("adjusted=true")
    }

    @Test
    fun `propaga Authorization Bearer desde TokenPolicy`() = runTest {
        coEvery { tokenPolicy.resolveToken() } returns "abc.def.ghi"

        server.enqueue(json(200, """
            [
              {"symbol":"AAPL","last":100.0,"ts":"2024-01-01T00:00:00Z","open":99.0,"high":101.0,"low":98.0,"prevClose":97.5,"currency":"USD","source":"X","stale":false}
            ]
        """.trimIndent()))

        client.getQuotes(listOf("AAPL"))

        val req = server.takeRequest()
        assertEquals("Bearer abc.def.ghi", req.getHeader("Authorization"))
    }

    @Test
    fun `mapea 400 a BAD_REQUEST`() = runTest {
        server.enqueue(json(400, """{"error":"bad"}"""))
        val ex = assertFailsWith<PriceDataClientException> {
            client.getQuotes(listOf("AAPL"))
        }
        assertEquals(PriceDataClientException.Kind.BAD_REQUEST, ex.kind)
    }

    @Test
    fun `mapea 404 a NOT_FOUND`() = runTest {
        server.enqueue(json(404, """{"error":"not found"}"""))
        val ex = assertFailsWith<PriceDataClientException> {
            client.getHistorical("AAPL", Timeframe.D1, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), null)
        }
        assertEquals(PriceDataClientException.Kind.NOT_FOUND, ex.kind)
    }

    @Test
    fun `mapea 429 a RATE_LIMIT`() = runTest {
        server.enqueue(json(429, """{"error":"too many"}"""))
        val ex = assertFailsWith<PriceDataClientException> {
            client.getQuotes(listOf("AAPL"))
        }
        assertEquals(PriceDataClientException.Kind.RATE_LIMIT, ex.kind)
    }

    @Test
    fun `mapea 5xx a SERVER_ERROR`() = runTest {
        server.enqueue(json(503, """{"error":"down"}"""))
        val ex = assertFailsWith<PriceDataClientException> {
            client.getQuotes(listOf("AAPL"))
        }
        assertEquals(PriceDataClientException.Kind.SERVER_ERROR, ex.kind)
    }

    // ---- Helpers
    private fun json(code: Int, body: String) =
        MockResponse()
            .setResponseCode(code)
            .setBody(body.trimIndent())
            .addHeader("Content-Type", "application/json")
}