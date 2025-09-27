package com.notivest.alert_engine.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID


@Entity
@Table(
    name = "alert_event",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_alertevent_rule_fingerprint",
            columnNames = ["rule_id", "fingerprint"]
        )
    ]
)
class AlertEvent(
    @Id
    @UuidGenerator
    @GeneratedValue
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    var rule : AlertRule,

    @Column(name = "triggered_at", nullable = false)
    val triggeredAt : OffsetDateTime,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var payload : Map<String,Any> = emptyMap(),

    @Column(nullable = false, length = 120)
    var fingerprint: String,

    @Column(nullable = false)
    var sent : Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime? = null
) {
}