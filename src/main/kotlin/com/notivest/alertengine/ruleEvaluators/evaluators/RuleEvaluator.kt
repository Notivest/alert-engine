package com.notivest.alertengine.ruleEvaluators.evaluators

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.ruleEvaluators.data.PriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import kotlin.reflect.KClass

interface RuleEvaluator<TParams : Any> {
    fun getKind(): AlertKind
    fun getParamsType(): KClass<TParams>

    fun evaluate(
        ctx: RuleEvaluationContext,
        rule: AlertRule,
        prices: PriceSeries,
        params: TParams
    ): RuleEvaluationResult
}