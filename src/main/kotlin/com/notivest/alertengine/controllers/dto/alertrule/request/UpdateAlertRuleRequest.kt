package com.notivest.alertengine.controllers.dto.alertrule.request

import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import jakarta.validation.constraints.PositiveOrZero

data class UpdateAlertRuleRequest(
    val params: Map<String, Any>? = null,
    val status: RuleStatus? = null,
    val notifyMinSeverity: SeverityAlert? = null,
    @field:PositiveOrZero
    val debounceSeconds: Long? = null
)