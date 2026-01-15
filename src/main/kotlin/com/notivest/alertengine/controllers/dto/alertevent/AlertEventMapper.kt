package com.notivest.alertengine.controllers.dto.alertevent

import com.notivest.alertengine.models.AlertEvent

fun AlertEvent.toResponse() = AlertEventResponse(
    id = requireNotNull(this.id),
    ruleId = requireNotNull(this.rule.id),
    ruleTitle = this.rule.title,
    ruleNote = this.rule.note,
    triggeredAt = this.triggeredAt.toString(),
    payload = this.payload,
    fingerprint = this.fingerprint,
    severity = this.severity.name,
    sent = this.sent,
    createdAt = this.createdAt?.toString(),
    updatedAt = this.updatedAt?.toString(),
)
