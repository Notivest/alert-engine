package com.notivest.alertengine.dto.alertrule.request

import com.notivest.alertengine.models.enums.AlertKind
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.models.enums.Timeframe
import org.springframework.data.domain.Pageable

data class GetAlertQuery(
    val status: RuleStatus? = null,
    val symbol: String? = null,
    val kind: AlertKind? = null,
    val timeframe: Timeframe? = null,
)
