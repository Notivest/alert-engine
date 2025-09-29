package com.notivest.alertengine.pricefetcher.tokenprovider

import com.notivest.alertengine.pricefetcher.config.PriceDataClientProperties
import org.springframework.stereotype.Component

@Component
class TokenPolicy(
    private val props: PriceDataClientProperties,
    private val propagated: PropagatedJwtTokenProvider,
    private val service: ServiceAccountTokenProvider
) {
    suspend fun resolveToken(): String? {
        if (props.auth.enablePropagated) propagated.getToken()?.let { return it }
        if (props.auth.enableServiceAccount) return service.getToken()
        return null
    }
}