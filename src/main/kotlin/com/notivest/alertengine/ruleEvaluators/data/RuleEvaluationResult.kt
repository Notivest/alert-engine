package com.notivest.alertengine.ruleEvaluators.data

import com.fasterxml.jackson.databind.node.ObjectNode
import com.notivest.alertengine.models.enums.SeverityAlert

data class RuleEvaluationResult(
    val triggered: Boolean,
    val severity: SeverityAlert,
    val fingerprint: String,
    val payload: ObjectNode? = null,
    val newState: ObjectNode? = null
)