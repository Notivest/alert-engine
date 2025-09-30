package com.notivest.alertengine.scheduler

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Component
class AlertEvaluationScheduler(
    private val job: AlertEvaluationJob,
    private val properties: AlertEvaluationSchedulerProperties,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val logger = LoggerFactory.getLogger(AlertEvaluationScheduler::class.java)
    private val running = AtomicBoolean(false)
    private val lastSuccessEpochSeconds = AtomicLong(0)

    private val cycleTimer = Timer.builder("alertengine.scheduler.cycle.latency")
        .description("Total latency per scheduled alert evaluation cycle")
        .register(meterRegistry)

    init {
        io.micrometer.core.instrument.Gauge.builder("alertengine.scheduler.last.success.epoch", lastSuccessEpochSeconds) {
            it.get().toDouble()
        }
            .description("Epoch seconds of the last successful alert evaluation cycle")
            .register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${alertengine.scheduler.cadence:PT60S}")
    fun runScheduledCycle() {
        if (!properties.enabled) {
            logger.debug("alert-eval-cycle-disabled message=\"scheduler disabled\"")
            return
        }

        if (!running.compareAndSet(false, true)) {
            logger.warn("alert-eval-cycle-skipped message=\"previous execution still running\"")
            return
        }

        val cycleId = UUID.randomUUID()
        val startedAt = Instant.now(clock)
        val timerSample = Timer.start(meterRegistry)
        logger.info("alert-eval-cycle-start cycleId={} startedAt={} cadenceSeconds={}", cycleId, startedAt, properties.cadence.seconds)

        try {
            runBlocking {
                job.execute(cycleId, startedAt)
            }
            lastSuccessEpochSeconds.set(startedAt.epochSecond)
            logger.info("alert-eval-cycle-success cycleId={} durationMs={}", cycleId, java.time.Duration.between(startedAt, Instant.now(clock)).toMillis())
        } catch (ex: Exception) {
            logger.error("alert-eval-cycle-error cycleId={} message=\"unexpected failure\"", cycleId, ex)
        } finally {
            timerSample.stop(cycleTimer)
            running.set(false)
        }
    }

    fun lastSuccessfulExecution(): Instant? =
        lastSuccessEpochSeconds.get().takeIf { it > 0 }?.let { Instant.ofEpochSecond(it) }
}

