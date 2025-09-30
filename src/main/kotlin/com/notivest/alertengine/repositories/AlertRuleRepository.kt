package com.notivest.alertengine.repositories

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.RuleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.Optional
import java.util.UUID

interface AlertRuleRepository : JpaRepository<AlertRule, UUID>, JpaSpecificationExecutor<AlertRule> {
    fun findByUserIdAndStatusOrderByUpdatedAtDesc(userId: UUID, status: RuleStatus): List<AlertRule>
    fun findAllByUserIdAndStatus(userId: UUID, status: RuleStatus, pageable: Pageable): Page<AlertRule>

    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<AlertRule>

    fun findAllByStatus(status: RuleStatus): List<AlertRule>
}