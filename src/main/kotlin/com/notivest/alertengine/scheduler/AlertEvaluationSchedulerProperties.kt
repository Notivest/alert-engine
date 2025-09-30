package com.notivest.alertengine.scheduler

import java.time.Duration

class AlertEvaluationSchedulerProperties {
    var enabled: Boolean = true
    var cadence: Duration = Duration.ofSeconds(60)
    var maxParallelEvaluations: Int = 4
    var historyLookback: Int = 200
}

