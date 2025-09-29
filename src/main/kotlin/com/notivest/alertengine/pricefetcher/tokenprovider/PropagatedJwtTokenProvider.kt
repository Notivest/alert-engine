package com.notivest.alertengine.pricefetcher.tokenprovider

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.core.AbstractOAuth2Token
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.security.core.context.ReactiveSecurityContextHolder

@Component
class PropagatedJwtTokenProvider : AuthTokenProvider {
    override suspend fun getToken(): String? {
        // 1) Servlet / MVC (ThreadLocal)
        tokenFromServlet()?.let { return it }

        // 2) WebFlux / Reactivo (Reactor Context)
        return tokenFromReactive()
    }

    private fun tokenFromServlet(): String? {
        val auth: Authentication? = SecurityContextHolder.getContext()?.authentication
        val jwtAuth = auth as? JwtAuthenticationToken ?: return null
        // Ojo: en JwtAuthenticationToken, lo correcto es leer token.tokenValue (no credentials)
        return (jwtAuth.token as? AbstractOAuth2Token)?.tokenValue
    }

    private suspend fun tokenFromReactive(): String? {
        val sc = ReactiveSecurityContextHolder.getContext().awaitSingleOrNull() ?: return null
        val jwtAuth = sc.authentication as? JwtAuthenticationToken ?: return null
        return (jwtAuth.token as? AbstractOAuth2Token)?.tokenValue
    }
}