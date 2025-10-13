package com.notivest.alertengine.repositories

import com.notivest.alertengine.models.AlertEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

interface AlertEventRepository : JpaRepository<AlertEvent, UUID> {
    fun existsByRuleIdAndFingerprint(ruleId: UUID, fingerprint: String): Boolean
    fun findAllByRuleId(ruleId: UUID, pageable: Pageable): Page<AlertEvent>
    fun findAllByRuleUserId(userId: UUID, pageable: Pageable): Page<AlertEvent>
    fun findByRuleIdAndFingerprint(ruleId: UUID, fingerprint: String): AlertEvent?
    fun countByRuleId(ruleId: UUID): Long

    @Modifying
    @Transactional
    @Query(
        value = """
    INSERT INTO alert_event (
      id, rule_id, triggered_at, payload, fingerprint, severity, sent, created_at, updated_at
    )
    SELECT
      :id, :ruleId, :triggeredAt, CAST(:payload AS JSON), :fingerprint, :severity, :sent, :createdAt, :updatedAt
    WHERE NOT EXISTS (
      SELECT 1 FROM alert_event WHERE rule_id = :ruleId AND fingerprint = :fingerprint
    )
  """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("ruleId") ruleId: UUID,
        @Param("triggeredAt") triggeredAt: OffsetDateTime,
        @Param("payload") payload: String,                // <- String JSON
        @Param("fingerprint") fingerprint: String,
        @Param("severity") severity: String,
        @Param("sent") sent: Boolean,
        @Param("createdAt") createdAt: OffsetDateTime,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int


}