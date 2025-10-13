package com.notivest.alertengine.controllers.dto.alertevent

import java.util.UUID

data class AlertEventResponse(
    val id: UUID,
    val ruleId: UUID,
    val triggeredAt: String,
    val payload: Map<String, Any>,
    val fingerprint: String,
    val severity: String,
    val sent: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
)