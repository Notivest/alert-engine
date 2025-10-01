package com.notivest.alertengine.notification

import com.notivest.alertengine.models.AlertEvent
import org.springframework.stereotype.Service

@Service
class NotificationServiceImpl() : NotificationService {
    override fun send(event: AlertEvent) {
        TODO("Not yet implemented")
    }
}