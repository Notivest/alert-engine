package com.notivest.alertengine.repositories.spec

import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object AlertRuleSpecs {

    fun byUser(userId: UUID): Specification<AlertRule> =
        Specification { root, _, cb -> cb.equal(root.get<UUID>("userId"), userId) }

    fun status(status: RuleStatus?): Specification<AlertRule> =
        Specification { root, _, cb -> status?.let { cb.equal(root.get<RuleStatus>("status"), it) } ?: cb.conjunction() }

    fun symbol(symbol: String?): Specification<AlertRule> =
        Specification { root, _, cb -> symbol?.let { cb.equal(root.get<String>("symbol"), it) } ?: cb.conjunction() }

    fun kind(kind: AlertKind?): Specification<AlertRule> =
        Specification { root, _, cb -> kind?.let { cb.equal(root.get<AlertKind>("kind"), it) } ?: cb.conjunction() }

    fun timeframe(tf: Timeframe?): Specification<AlertRule> =
        Specification { root, _, cb -> tf?.let { cb.equal(root.get<Timeframe>("timeframe"), it) } ?: cb.conjunction() }
}
