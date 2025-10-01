package com.notivest.alertengine.scheduler

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class ErrorMetrics(
    private val meterRegistry: MeterRegistry,
) {
    fun increment(category: String) {
        meterRegistry.counter("alertengine.scheduler.errors", "category", category).increment()
    }
}