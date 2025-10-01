package com.notivest.alertengine.service.interfaces

import com.notivest.alertengine.controllers.dto.alertrule.request.CreateAlertRuleRequest
import com.notivest.alertengine.controllers.dto.alertrule.request.GetAlertQuery
import com.notivest.alertengine.controllers.dto.alertrule.request.UpdateAlertRuleRequest
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.RuleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface AlertRuleService {

    fun list(userId: UUID, query: GetAlertQuery, pageable: Pageable): Page<AlertRule>

    fun create(userId: UUID, command: CreateAlertRuleRequest): AlertRule

    fun update(userId: UUID, alertId: UUID, command: UpdateAlertRuleRequest): AlertRule

    fun setStatus(userId: UUID, alertId: UUID, status: RuleStatus): AlertRule

    fun get(userId: UUID, alertId: UUID): AlertRule
}