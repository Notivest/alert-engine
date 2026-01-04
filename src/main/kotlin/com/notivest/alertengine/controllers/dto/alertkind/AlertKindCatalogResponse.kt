package com.notivest.alertengine.controllers.dto.alertkind

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.Timeframe

data class AlertKindCatalogResponse(
    val version: String,
    val kinds: List<AlertKindDefinition>,
)

data class AlertKindDefinition(
    val kind: AlertKind,
    val description: String,
    val timeframes: List<Timeframe>,
    val params: List<AlertKindParam>,
    val examples: List<AlertKindExample>,
)

data class AlertKindExample(
    val timeframe: Timeframe,
    val params: Map<String, Any>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AlertKindParam(
    val name: String,
    val type: AlertKindParamType,
    val required: Boolean,
    val min: Number? = null,
    val max: Number? = null,
    val exclusiveMin: Boolean? = null,
    val exclusiveMax: Boolean? = null,
    val values: List<String>? = null,
    @JsonProperty("default") val defaultValue: Any? = null,
    val description: String? = null,
)

enum class AlertKindParamType(@JsonValue val value: String) {
    INT("int"),
    NUMBER("number"),
    STRING("string"),
    BOOLEAN("boolean"),
    ENUM("enum"),
}
