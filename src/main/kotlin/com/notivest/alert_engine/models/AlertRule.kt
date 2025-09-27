package com.notivest.alert_engine.models

import com.notivest.alert_engine.models.converters.DurationSecondsConverter
import com.notivest.alert_engine.models.enums.AlertKind
import com.notivest.alert_engine.models.enums.RuleStatus
import com.notivest.alert_engine.models.enums.Timeframe
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID
import java.time.Duration


@Entity
@Table(
    name = "alert_rules",
    indexes = [
        Index(name = "ix_rules_user_symbol", columnList = "user_id,symbol"),
        Index(name = "ix_rules_status", columnList = "status"),
        Index(name = "ix_rules_timeframe", columnList = "timeframe")
    ]
)
class AlertRule(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false , length = 20)
    val symbol : String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val kind : AlertKind,

    // Json especifico por tipo de alerta
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var params : Map<String,Any> = emptyMap(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var timeframe: Timeframe = Timeframe.D1,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var status: RuleStatus = RuleStatus.ACTIVE,

    @Convert(converter = DurationSecondsConverter::class)
    @Column(name = "debounce_secs")
    val debounceTime: Duration? = null,

    @Column(name = "last_triggered_at")
    val lastTriggeredAt : OffsetDateTime? = null,

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime? = null


) {

    override fun equals(other: Any?) = this === other || (other is AlertRule && this.id == other.id)

    override fun hashCode(): Int {
        return id.hashCode()
    }
}