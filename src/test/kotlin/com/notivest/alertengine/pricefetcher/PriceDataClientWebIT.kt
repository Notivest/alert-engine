package com.notivest.alertengine.pricefetcher

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notivest.alertengine.pricefetcher.client.PriceDataClientWeb
import com.notivest.alertengine.pricefetcher.config.PriceDataClientProperties
import com.notivest.alertengine.pricefetcher.tokenprovider.PropagatedJwtTokenProvider
import com.notivest.alertengine.pricefetcher.tokenprovider.ServiceAccountTokenProvider
import com.notivest.alertengine.pricefetcher.tokenprovider.TokenPolicy
import io.github.cdimascio.dotenv.dotenv
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.netty.channel.ChannelOption
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Tag("integration")
class PriceDataClientWebIT {

    private val dotenv = dotenv {
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    private fun env(name: String): String? =
        System.getenv(name) ?: dotenv[name]

    @Test
    fun `getQuotes devuelve datos reales usando service account`() {
        val issuer       = env("JWT_ISSUER_URI")
        val audience     = env("JWT_AUDIENCE")
        val clientId     = env("PRICEFETCHER_CLIENT_ID")
        val clientSecret = env("PRICEFETCHER_CLIENT_SECRET")
        val scope        = env("AUTH_SCOPE") ?: ""
        val baseUrl      = env("PRICEDATA_BASE_URL")
        val symbol       = env("PRICEDATA_TEST_SYMBOL") ?: "AAPL"

        assumeTrue(!issuer.isNullOrBlank(),       "Falta JWT_ISSUER_URI")
        assumeTrue(!audience.isNullOrBlank(),     "Falta JWT_AUDIENCE")
        assumeTrue(!clientId.isNullOrBlank(),     "Falta PRICEFETCHER_CLIENT_ID")
        assumeTrue(!clientSecret.isNullOrBlank(), "Falta PRICEFETCHER_CLIENT_SECRET")
        assumeTrue(!baseUrl.isNullOrBlank(),      "Falta PRICEDATA_BASE_URL")

        val props = PriceDataClientProperties().apply {
            this.baseUrl = baseUrl!!.removeSuffix("/")
            auth = PriceDataClientProperties.Auth().apply {
                enablePropagated = false
                enableServiceAccount = true
            }
            retries = PriceDataClientProperties.Retries().apply {
                max = 0
            }
        }

        val mapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(props.readTimeoutMs.toLong()))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeoutMs)
            .compress(true)

        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { codecs ->
                codecs.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper))
                codecs.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(mapper))
            }
            .build()

        val webClient = WebClient.builder()
            .baseUrl(props.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchangeStrategies)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()

        val serviceAccount = ServiceAccountTokenProvider(
            issuer = issuer!!,
            audience = audience!!,
            clientId = clientId!!,
            clientSecret = clientSecret!!,
            scope = scope,
            builder = WebClient.builder()
        )

        val tokenPolicy = TokenPolicy(
            props = props,
            propagated = PropagatedJwtTokenProvider(),
            service = serviceAccount
        )

        val client = PriceDataClientWeb(
            client = webClient,
            props = props,
            tokenPolicy = tokenPolicy,
            meterRegistry = SimpleMeterRegistry(),
            mapper = mapper
        )

        val quotes = runBlocking {
            client.getQuotes(listOf(symbol))
        }

        assertThat(quotes).containsKey(symbol)
        println(quotes[symbol])
        assertThat(quotes[symbol]).isNotNull
    }
}