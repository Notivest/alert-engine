package com.notivest.alertengine.repositories

import com.notivest.alertengine.models.AlertEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AlertEventRepository : JpaRepository<AlertEvent, UUID> {
    fun existsByRuleIdAndFingerprint(ruleId: UUID, fingerprint: String): Boolean
    fun findTop50ByRuleIdOrderByTriggeredAtDesc(ruleId: UUID): List<AlertEvent>
    fun findAllByRuleId(ruleId: UUID, pageable: Pageable): Page<AlertEvent>
}