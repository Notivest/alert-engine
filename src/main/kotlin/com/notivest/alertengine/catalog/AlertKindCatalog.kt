package com.notivest.alertengine.catalog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindCatalogResponse
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindDefinition
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindExample
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindParam
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindParamType
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.Timeframe
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class AlertKindCatalog(
    private val objectMapper: ObjectMapper,
) {

    fun getCatalog(): AlertKindCatalogResponse {
        val schemaResources = loadSchemaResources()
        val kinds = schemaResources
            .map { resource -> toDefinition(resource) }
            .sortedBy { it.kind.name }
        val version = computeVersion(schemaResources)

        return AlertKindCatalogResponse(
            version = version,
            kinds = kinds,
        )
    }

    private fun loadSchemaResources(): List<SchemaResource> {
        val resources = resolver.getResources("classpath:schemas/alerts/*.json")
        return resources
            .sortedBy { it.filename.orEmpty() }
            .map { resource ->
                val bytes = resource.inputStream.use { it.readBytes() }
                val node = objectMapper.readTree(bytes)
                SchemaResource(resource, bytes, node)
            }
    }

    private fun toDefinition(schemaResource: SchemaResource): AlertKindDefinition {
        val schema = schemaResource.node
        val kindName = schema.path("title").asText().ifBlank {
            schemaResource.resource.filename.orEmpty().removeSuffix(".json")
        }
        val kind = AlertKind.valueOf(kindName)
        val description = schema.path("description").asText(kind.name)
        val required = schema.path("required").map { it.asText() }.toSet()
        val params = schema.path("properties")
            .fields()
            .asSequence()
            .map { (name, prop) -> toParam(name, prop, required.contains(name)) }
            .toList()
        val examples = schema.path("examples")
            .takeIf { it.isArray }
            ?.mapNotNull { example -> example.takeIf { it.isObject }?.toExample() }
            .orEmpty()

        return AlertKindDefinition(
            kind = kind,
            description = description,
            timeframes = allTimeframes,
            params = params,
            examples = examples,
        )
    }

    private fun toParam(name: String, prop: JsonNode, required: Boolean): AlertKindParam {
        val enumValues = prop.path("enum").takeIf { it.isArray }?.map { it.asText() }
        val type = when {
            enumValues != null -> AlertKindParamType.ENUM
            prop.path("type").asText() == "integer" -> AlertKindParamType.INT
            prop.path("type").asText() == "number" -> AlertKindParamType.NUMBER
            prop.path("type").asText() == "boolean" -> AlertKindParamType.BOOLEAN
            else -> AlertKindParamType.STRING
        }
        val (min, exclusiveMin) = extractMin(prop)
        val (max, exclusiveMax) = extractMax(prop)

        return AlertKindParam(
            name = name,
            type = type,
            required = required,
            min = min,
            max = max,
            exclusiveMin = exclusiveMin,
            exclusiveMax = exclusiveMax,
            values = enumValues,
            defaultValue = nodeToValue(prop.path("default")),
            description = prop.path("description").takeIf { it.isTextual }?.asText(),
        )
    }

    private fun JsonNode.toExample(): AlertKindExample = AlertKindExample(
        timeframe = Timeframe.D1,
        params = nodeToValue(this) as Map<String, Any>,
    )

    private fun extractMin(node: JsonNode): Pair<Number?, Boolean?> {
        val exclusiveNode = node.path("exclusiveMinimum")
        if (!exclusiveNode.isMissingNode && !exclusiveNode.isNull) {
            if (exclusiveNode.isNumber) {
                return numberValue(exclusiveNode) to true
            }
            if (exclusiveNode.isBoolean && exclusiveNode.asBoolean()) {
                return numberValue(node.path("minimum")) to true
            }
        }
        val minimum = numberValue(node.path("minimum"))
        return minimum to null
    }

    private fun extractMax(node: JsonNode): Pair<Number?, Boolean?> {
        val exclusiveNode = node.path("exclusiveMaximum")
        if (!exclusiveNode.isMissingNode && !exclusiveNode.isNull) {
            if (exclusiveNode.isNumber) {
                return numberValue(exclusiveNode) to true
            }
            if (exclusiveNode.isBoolean && exclusiveNode.asBoolean()) {
                return numberValue(node.path("maximum")) to true
            }
        }
        val maximum = numberValue(node.path("maximum"))
        return maximum to null
    }

    private fun numberValue(node: JsonNode): Number? {
        if (node.isMissingNode || node.isNull) return null
        return when {
            node.isInt -> node.intValue()
            node.isLong -> node.longValue()
            node.isBigInteger -> node.bigIntegerValue()
            node.isBigDecimal -> node.decimalValue()
            node.isDouble || node.isFloat -> node.doubleValue()
            node.isNumber -> node.numberValue()
            else -> null
        }
    }

    private fun nodeToValue(node: JsonNode): Any? {
        if (node.isMissingNode || node.isNull) return null
        return when {
            node.isTextual -> node.asText()
            node.isInt -> node.intValue()
            node.isLong -> node.longValue()
            node.isBigInteger -> node.bigIntegerValue()
            node.isBigDecimal -> node.decimalValue()
            node.isDouble || node.isFloat -> node.doubleValue()
            node.isNumber -> node.numberValue()
            node.isBoolean -> node.booleanValue()
            node.isObject || node.isArray -> objectMapper.convertValue(node, Any::class.java)
            else -> node.asText()
        }
    }

    private fun computeVersion(schemaResources: List<SchemaResource>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        schemaResources
            .sortedBy { it.resource.filename.orEmpty() }
            .forEach { resource ->
                val name = resource.resource.filename.orEmpty()
                digest.update(name.toByteArray(Charsets.UTF_8))
                digest.update(resource.bytes)
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class SchemaResource(
        val resource: Resource,
        val bytes: ByteArray,
        val node: JsonNode,
    )

    companion object {
        private val resolver = PathMatchingResourcePatternResolver()
        private val allTimeframes = Timeframe.values().toList()
    }
}
