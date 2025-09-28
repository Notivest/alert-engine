package com.notivest.alertengine.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.notivest.alertengine.exception.InvalidParamsException
import com.notivest.alertengine.models.enums.AlertKind
import org.springframework.stereotype.Component

@Component
class AlertParamsValidator(private val mapper: ObjectMapper) {

    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    private val schemas: Map<AlertKind, JsonSchema> = mapOf(
        AlertKind.PRICE_ABOVE to load("schemas/alerts/PRICE_ABOVE.json"),
        AlertKind.PRICE_BELOW to load("schemas/alerts/PRICE_BELOW.json")
    )

    private fun load(path: String): JsonSchema {
        val inStream = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Schema not found: $path"
        }
        return inStream.use { factory.getSchema(it) }
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<AlertKind, JsonSchema>()

    private fun schemaFor(kind: AlertKind): JsonSchema =
        cache.computeIfAbsent(kind) { load("schemas/alerts/${it.name}.json") }

    fun validate(kind: AlertKind, params: Map<String, Any>) {
        val node = mapper.valueToTree<JsonNode>(params)
        val errors = schemaFor(kind).validate(node)
        if (errors.isNotEmpty()) {
            val detail = errors.joinToString("; ") { "${it.message}" }
            throw InvalidParamsException("Invalid params for $kind: $detail")
        }
    }
}