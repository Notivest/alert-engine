package com.notivest.alertengine.pricefetcher.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.security.oauth2.server.resource.web.reactive.function.client.ServletBearerExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
class PriceDataClientConfig {

    @Bean
    @ConfigurationProperties("pricedata")
    fun priceDataClientProperties() = PriceDataClientProperties()

    @Bean
    fun priceDataHttpClient(props: PriceDataClientProperties): HttpClient {
        val provider = ConnectionProvider.builder("pricedata-pool")
            .maxConnections(50)
            .pendingAcquireMaxCount(200)
            .metrics(true) // métricas del pool (si Micrometer está presente)
            .build()

        return HttpClient.create(provider)
            .responseTimeout(Duration.ofMillis(props.readTimeoutMs.toLong()))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeoutMs)
            .compress(true)
        // .metrics(true)  // ver punto 2) más abajo
    }

    @Bean
    fun priceDataWebClient(
        httpClient: HttpClient,
        props: PriceDataClientProperties,
        mapper: ObjectMapper
    ): WebClient {
        val exchange = ExchangeStrategies.builder()
            .codecs {
                it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper))
                it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(mapper))
            }
            .build()

        return WebClient.builder()
            .baseUrl(props.baseUrl.removeSuffix("/")) // evita // en las URIs
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchange)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            // Propaga Authorization: Bearer del SecurityContext si existe (servlet stack)
            .filter(ServletBearerExchangeFilterFunction())
            .build()
    }

    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}