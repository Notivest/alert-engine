package com.notivest.alertengine.ruleEvaluators.data

import com.notivest.alertengine.models.enums.Timeframe
import java.time.Instant

data class RuleEvaluationContext(
    val evaluatedAt: Instant,
    val timeframe: Timeframe
)