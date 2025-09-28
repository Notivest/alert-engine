package com.notivest.alertengine.dto.alertrule

import com.notivest.alertengine.dto.alertrule.response.AlertRuleResponse
import com.notivest.alertengine.models.AlertRule

fun AlertRule.toResponse() = AlertRuleResponse(
    id = requireNotNull(this.id),
    symbol = this.symbol,
    kind = this.kind.name,
    params = this.params,
    timeframe = this.timeframe.name,
    status = this.status.name,
    debounceSeconds = this.debounceTime?.seconds,
    createdAt = this.createdAt?.toString(),
    updatedAt = this.updatedAt?.toString()
)