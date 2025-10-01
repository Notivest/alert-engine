package com.notivest.alertengine.scheduler

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult

sealed interface RuleRunResult {
    val rule: AlertRule

    data class Fired(
        override val rule: AlertRule,
        val result: RuleEvaluationResult,
    ) : RuleRunResult

    data class NoOp(
        override val rule: AlertRule,
        val reason: String,
    ) : RuleRunResult

    data class Error(
        override val rule: AlertRule,
        val exception: Throwable,
    ) : RuleRunResult
}