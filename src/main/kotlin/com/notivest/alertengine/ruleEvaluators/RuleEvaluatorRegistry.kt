package com.notivest.alertengine.ruleEvaluators

import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.ruleEvaluators.exceptions.DuplicateRuleEvaluatorException
import com.notivest.alertengine.ruleEvaluators.exceptions.MissingRuleEvaluatorException
import org.springframework.stereotype.Component

@Component
class RuleEvaluatorRegistry(
    evaluators: List<RuleEvaluator<*>>
) {

    private val byKind: Map<AlertKind, RuleEvaluator<*>> = evaluators
        .groupBy { it.getKind() }
        .also { grouped ->
            grouped.forEach { (kind, list) ->
                if (list.size > 1) {
                    throw DuplicateRuleEvaluatorException(
                        kind,
                        list.map { it::class.qualifiedName ?: it::class.simpleName ?: "Unknown" }
                    )
                }
            }
        }
        .mapValues { (_, list) -> list.single() }
        .toMap()

    fun get(kind: AlertKind): RuleEvaluator<*>? = byKind[kind]

    fun require(kind: AlertKind): RuleEvaluator<*> =
        get(kind) ?: throw MissingRuleEvaluatorException(kind)

    fun kinds(): Set<AlertKind> = byKind.keys
}