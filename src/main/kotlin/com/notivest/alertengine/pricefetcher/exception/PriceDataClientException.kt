package com.notivest.alertengine.pricefetcher.exception

class PriceDataClientException(
    val kind: Kind,
    val status: Int? = null,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    enum class Kind { NETWORK, TIMEOUT, RATE_LIMIT, BAD_REQUEST, NOT_FOUND, SERVER_ERROR, INVALID_RESPONSE }
}