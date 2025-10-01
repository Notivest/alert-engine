package com.notivest.alertengine.scheduler


import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SchedulerConfig {

    @Bean
    @ConfigurationProperties("alertengine.scheduler")
    fun alertEvaluationSchedulerProperties() = AlertEvaluationSchedulerProperties()
}