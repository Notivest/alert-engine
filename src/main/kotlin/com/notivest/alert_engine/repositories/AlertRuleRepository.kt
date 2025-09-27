package com.notivest.alert_engine.repositories

import com.notivest.alert_engine.models.AlertRule
import com.notivest.alert_engine.models.enums.RuleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AlertRuleRepository : JpaRepository<AlertRule, UUID> {
    fun findByUserIdAndStatusOrderByUpdatedAtDesc(userId: UUID, status: RuleStatus): List<AlertRule>
    fun findAllByUserIdAndStatus(userId: UUID, status: RuleStatus, pageable: Pageable): Page<AlertRule>
}