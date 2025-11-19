package com.notivest.alertengine.notification

import com.notivest.alertengine.models.AlertEvent

fun interface NotificationService {
    suspend fun send(event: AlertEvent)
}
