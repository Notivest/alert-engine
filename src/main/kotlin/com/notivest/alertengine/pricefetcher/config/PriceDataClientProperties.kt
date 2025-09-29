package com.notivest.alertengine.pricefetcher.config

data class PriceDataClientProperties(
    var baseUrl: String = "",
    var connectTimeoutMs: Int = 2_000,
    var readTimeoutMs: Int = 5_000,
    var retries: Retries = Retries(),
    var auth: Auth = Auth()
) {
    data class Retries(
        var max: Int = 3,
        var baseDelayMs: Long = 200,
        var maxDelayMs: Long = 2_000,
        var jitterMs: Long = 150
    )
    data class Auth(
        var enablePropagated: Boolean = true,
        var enableServiceAccount: Boolean = true
    )
}