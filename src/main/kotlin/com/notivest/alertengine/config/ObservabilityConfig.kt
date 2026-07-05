package com.notivest.alertengine.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.newrelic.NewRelicConfig
import io.micrometer.newrelic.NewRelicMeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.StringUtils
import java.time.Duration

@Configuration
class ObservabilityConfig(
    @Value("\${spring.application.name:alert-engine}") private val serviceName: String,
    @Value("\${management.newrelic.api-key:}") private val apiKey: String,
    @Value("\${management.newrelic.account-id:}") private val accountId: String,
    @Value("\${management.newrelic.uri:https://insights-collector.newrelic.com}") private val uri: String,
) {
    @Bean
    fun newRelicConfig(): NewRelicConfig {
        val shouldEnable = isValid(apiKey) && isValid(accountId)

        return object : NewRelicConfig {
            override fun get(key: String): String? = null

            override fun apiKey(): String = if (shouldEnable) apiKey else "dummy"

            override fun accountId(): String = if (shouldEnable) accountId else "dummy"

            override fun uri(): String = uri

            override fun enabled(): Boolean = shouldEnable

            override fun step(): Duration = Duration.ofMinutes(1)
        }
    }

    @Bean
    fun newRelicRegistry(config: NewRelicConfig): NewRelicMeterRegistry =
        NewRelicMeterRegistry.builder(config).build()

    @Bean
    fun meterRegistryCustomizer(): MeterRegistryCustomizer<MeterRegistry> =
        MeterRegistryCustomizer { registry ->
            registry.config().commonTags("service", serviceName)
        }

    private fun isValid(value: String?): Boolean =
        StringUtils.hasText(value) && !value.equals("null", ignoreCase = true)
}
