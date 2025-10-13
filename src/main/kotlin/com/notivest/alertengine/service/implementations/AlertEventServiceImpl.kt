package com.notivest.alertengine.service.implementations

import com.notivest.alertengine.models.AlertEvent
import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.service.interfaces.AlertEventService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AlertEventServiceImpl(
    private val alertEventRepository: AlertEventRepository,
) : AlertEventService {

    @Transactional(readOnly = true)
    override fun list(userId: UUID, pageable: Pageable): Page<AlertEvent> {
        return alertEventRepository.findAllByRuleUserId(userId, pageable)
    }
}