package com.notivest.alertengine.controllers.dto.alertrule.response

import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import java.time.OffsetDateTime
import java.util.UUID

data class AlertRuleResponse (
    val id: UUID,
    val symbol: String,
    val kind: String,
    val params: Map<String, Any>,
    val timeframe: String,
    val status: String,
    val notifyMinSeverity: String,
    val debounceSeconds: Long?,
    val createdAt: String?,
    val updatedAt: String?
)