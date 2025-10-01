package com.notivest.alertengine.notification

import com.notivest.alertengine.models.AlertEvent

fun interface NotificationService {
    fun send(event: AlertEvent)
}