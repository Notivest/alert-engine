package com.notivest.alertengine.notification

data class NotificationClientProperties(
    var baseUrl: String = "",
    var alertPath: String = "/api/v1/notify/alert",
    var alertTemplateKey: String = "alert-default"
)
