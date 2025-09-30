package com.notivest.alertengine.ruleEvaluators

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.ruleEvaluators.exceptions.InvalidParamsExceptionRule
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class RuleParamsConverter(
    private val mapper: ObjectMapper
) {
    fun <T : Any> convert(node: JsonNode, type: KClass<T>, kind: AlertKind): T {
        try {
            // treeToValue respeta @JsonProperty, default values de Kotlin con módulo Kotlin, etc.
            return mapper.treeToValue(node, type.java)
        } catch (e: MismatchedInputException) {
            val path = e.path.joinToString(separator = "") { ref ->
                when {
                    ref.fieldName != null -> ".${ref.fieldName}"
                    ref.index >= 0 -> "[${ref.index}]"
                    else -> ""
                }
            }
            val readablePath = if (path.isBlank()) "$" else "$$path"
            val details = e.originalMessage ?: e.message.orEmpty()
            throw InvalidParamsExceptionRule(
                "Failed to convert parameters for $kind to ${type.qualifiedName}. JSON path: $readablePath. Details: $details",
                e
            )
        } catch (e: Exception) {
            throw InvalidParamsExceptionRule(
                "Failed to convert parameters for $kind to ${type.qualifiedName} : ${e.message}",
                e
            )
        }
    }
}