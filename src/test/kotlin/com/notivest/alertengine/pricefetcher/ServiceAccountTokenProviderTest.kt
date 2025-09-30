package com.notivest.alertengine.pricefetcher

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notivest.alertengine.pricefetcher.tokenprovider.ServiceAccountTokenProvider
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

@Tag("integration")
class ServiceAccountTokenProviderIT {

    private val dotenv = dotenv {
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    private fun env(name: String): String? =
        System.getenv(name) ?: dotenv[name]

    @Test
    fun `client_credentials contra Auth0 devuelve JWT valido`() {
        runBlocking {
            val issuer       = env("JWT_ISSUER_URI")        // ej: https://dev-xxx.us.auth0.com
            val audience     = env("JWT_AUDIENCE")      // ej: https://my-api
            val clientId     = env("PRICEFETCHER_CLIENT_ID")
            val clientSecret = env("PRICEFETCHER_CLIENT_SECRET")
            val scope        = env("AUTH_SCOPE") ?: ""

            // Si falta algo, salto el test (no falla el build)
            assumeTrue(!issuer.isNullOrBlank(),        "Falta AUTH_ISSUER")
            assumeTrue(!audience.isNullOrBlank(),      "Falta AUTH_AUDIENCE")
            assumeTrue(!clientId.isNullOrBlank(),      "Falta AUTH_CLIENT_ID")
            assumeTrue(!clientSecret.isNullOrBlank(),  "Falta AUTH_CLIENT_SECRET")

            val provider = ServiceAccountTokenProvider(
                issuer = issuer!!,
                audience = audience!!,
                clientId = clientId!!,
                clientSecret = clientSecret!!,
                scope = scope,
                builder = WebClient.builder()
            )

            val token = provider.getToken()

            // --- Asserts "reales" sobre el JWT ---
            assertThat(token).isNotNull().isNotBlank()
            val parts = token!!.split(".")
            assertThat(parts.size).isEqualTo(3)

            val decoder = Base64.getUrlDecoder()
            val payloadJson = String(decoder.decode(parts[1]), StandardCharsets.UTF_8)

            val node = jacksonObjectMapper().readTree(payloadJson)

            // iss
            assertThat(node.get("iss").asText())
                .startsWith(issuer.removeSuffix("/"))

            // aud (puede ser string o array en Auth0)
            val audNode = node.get("aud")
            val audiences = when {
                audNode.isTextual -> listOf(audNode.asText())
                audNode.isArray   -> audNode.map { it.asText() }
                else              -> emptyList()
            }
            assertThat(audiences).contains(audience)

            // exp en el futuro (con margen de 60s)
            val expEpoch = node.get("exp").asLong()
            assertThat(Instant.ofEpochSecond(expEpoch))
                .isAfter(Instant.now().plusSeconds(60))
        }
    }
}