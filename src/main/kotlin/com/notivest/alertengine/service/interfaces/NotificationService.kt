package com.notivest.alertengine.service.interfaces

import com.notivest.alertengine.models.AlertEvent

/**
 * Abstraction that bridges alert events with the downstream notification pipeline.
 * Implementations are responsible for delivering the event according to the
 * NotificationService contract.
 */
fun interface NotificationService {
    fun send(event: AlertEvent)
}

