package com.notivest.alertengine.controllers.dto.alertrule.request

import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import com.notivest.alertengine.models.enums.Timeframe
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class CreateAlertRuleRequest (
    @field:NotBlank
    @field:Size(min = 1, max = 20)
    // ejemplo simple de ticker/símbolo en mayúsculas, ajustá si necesitás otros formatos
    @field:Pattern(regexp = "[A-Z0-9.:-]+")
    val symbol: String,

    @field:NotNull
    val kind: AlertKind,

    @field:NotNull
    @field:Size(min = 1) // que tenga al menos 1 parámetro
    val params: Map<String, Any>,

    @field:NotNull
    val timeframe: Timeframe,

    // opcional; si viene null, el service pone ACTIVE
    val status: RuleStatus? = null,

    val notifyMinSeverity: SeverityAlert? = null,

    @field:PositiveOrZero
    val debounceSeconds: Long? = null
)