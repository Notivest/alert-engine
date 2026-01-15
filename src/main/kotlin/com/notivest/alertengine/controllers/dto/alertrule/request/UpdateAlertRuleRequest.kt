package com.notivest.alertengine.controllers.dto.alertrule.request

import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.SeverityAlert
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class UpdateAlertRuleRequest(
    val params: Map<String, Any>? = null,
    @field:Size(max = 120)
    val title: String? = null,
    @field:Size(max = 500)
    val note: String? = null,
    val singleTrigger: Boolean? = null,
    val status: RuleStatus? = null,
    val severity: SeverityAlert? = null,
    @field:PositiveOrZero
    val debounceSeconds: Long? = null
)
