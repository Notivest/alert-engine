package com.notivest.alertengine.ruleEvaluators

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.ruleEvaluators.data.PriceSeries
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationContext
import com.notivest.alertengine.ruleEvaluators.data.RuleEvaluationResult
import com.notivest.alertengine.ruleEvaluators.evaluators.RuleEvaluator
import com.notivest.alertengine.validation.AlertParamsValidator
import org.springframework.stereotype.Component

@Component("ruleEvaluationOrchestrator")
class RuleEvaluationOrchestrator(
    private val registry: RuleEvaluatorRegistry,
    private val validator: AlertParamsValidator,
    private val converter: RuleParamsConverter,
    private val mapper: ObjectMapper,
) {
    fun evaluate(ctx: RuleEvaluationContext, rule: AlertRule, prices: PriceSeries): RuleEvaluationResult {
        val evaluator  = registry.require(rule.kind)
        val paramsType = evaluator.getParamsType()

        // 1) validar con Map (lo que tu validador espera)
        validator.validate(rule.kind, rule.params)  // <-- Map<String, Any>

        // 2) convertir a JsonNode para el converter
        val paramsNode: JsonNode = mapper.valueToTree(rule.params)

        // 3) convertir a tipo fuerte y evaluar
        val params = converter.convert(paramsNode, paramsType, rule.kind)

        @Suppress("UNCHECKED_CAST")
        return (evaluator as RuleEvaluator<Any>).evaluate(ctx, rule, prices, params)
    }
}