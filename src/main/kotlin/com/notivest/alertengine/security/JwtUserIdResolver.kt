package com.notivest.alertengine.security

import com.notivest.alertengine.exception.InvalidUserIdException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID
import java.nio.charset.StandardCharsets

@Component
class JwtUserIdResolver(
    @Value("\${JWT_USER_ID_CLAIM:claimId")
    private val userIdClaim: String,
) {
    fun requireUserId(jwt: Jwt): UUID =
        extractUserId(jwt) ?: throw InvalidUserIdException("JWT missing user identifier")

    fun extractUserId(jwt: Jwt): UUID? {
        // 1) Claim principal (puede ser UUID o un sub tipo "auth0|...")
        jwt.getClaimAsString(userIdClaim)
            ?.let { parseUuidOrNull(it) ?: stableUuidFrom(it) }
            ?.let { return it }

        // 2) Compat: "user_id"
        jwt.getClaimAsString("user_id")
            ?.let { parseUuidOrNull(it) ?: stableUuidFrom(it) }
            ?.let { return it }

        // 3) Fallback: sub
        jwt.subject?.let { return stableUuidFrom(it) }

        return null
    }

    private fun parseUuidOrNull(s: String?): UUID? =
        runCatching { if (!s.isNullOrBlank()) UUID.fromString(s) else null }.getOrNull()

    private fun stableUuidFrom(value: String): UUID =
        UUID.nameUUIDFromBytes(("notivest:$value").toByteArray(StandardCharsets.UTF_8))
}