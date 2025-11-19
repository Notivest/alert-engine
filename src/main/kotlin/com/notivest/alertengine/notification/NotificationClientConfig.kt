package com.notivest.alertengine.notification

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class NotificationClientConfig {

    @Bean
    @ConfigurationProperties("notification")
    fun notificationClientProperties() = NotificationClientProperties()

    @Bean
    fun notificationWebClient(
        builder: WebClient.Builder,
        notificationClientProperties: NotificationClientProperties
    ): WebClient {
        val baseUrl = notificationClientProperties.baseUrl.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("notification.base-url must be configured")

        return builder.clone()
            .baseUrl(baseUrl.removeSuffix("/"))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
