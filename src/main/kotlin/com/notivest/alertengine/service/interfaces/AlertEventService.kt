package com.notivest.alertengine.service.interfaces

import com.notivest.alertengine.models.AlertEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface AlertEventService {

    fun list(userId: UUID, pageable: Pageable): Page<AlertEvent>
}