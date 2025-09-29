package com.notivest.alertengine.pricefetcher.tokenprovider

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

@Component
class ServiceAccountTokenProvider(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuer: String,
    @Value("\${spring.security.oauth2.resourceserver.jwt.audience}")
    private val audience: String,
    @Value("\${serviceaccount.client-id}")
    private val clientId: String,
    @Value("\${serviceaccount.client-secret}")
    private val clientSecret: String,
    @Value("\${serviceaccount.scope:read:historical write:watchlist manage:prefetch}") // <- acá
    private val scope: String,
    builder: WebClient.Builder,
) : AuthTokenProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    // WebClient apuntando al dominio del issuer (Auth0)
    private val http: WebClient = builder
        .baseUrl(issuer.removeSuffix("/")) // ej: https://dev-xxx.us.auth0.com
        .build()

    // Cache simple: token + expiry
    @Volatile private var cached: Pair<String, Instant>? = null
    private val refreshMutex = Mutex()

    override suspend fun getToken(): String? {
        val now = Instant.now()

        // Si hay token y no está por expirar (margen 60s), devolverlo
        cached?.let { (tok, exp) ->
            if (exp.isAfter(now.plusSeconds(60))) return tok
        }

        // Evitamos carrera de múltiples refresh simultáneos
        return refreshMutex.withLock {
            // Rechequear dentro del lock (double-checked locking)
            cached?.let { (tok, exp) ->
                if (exp.isAfter(Instant.now().plusSeconds(60))) return@withLock tok
            }

            try {
                val body = mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "audience" to audience,
                    "grant_type" to "client_credentials",
                    "scope" to scope
                )

                val res = http.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Auth0ClientCredentialsResponse::class.java)
                    .awaitSingle()

                val token = res.access_token
                val exp = Instant.now().plusSeconds(res.expires_in ?: 3600L) // default 1h
                cached = token to exp

                token
            } catch (e: Exception) {
                log.warn("No se pudo obtener SA token de Auth0 (client_credentials): ${e.message}")
                null
            }
        }
    }

    private data class Auth0ClientCredentialsResponse(
        val access_token: String,
        val token_type: String? = null,
        val expires_in: Long? = null
    )
}