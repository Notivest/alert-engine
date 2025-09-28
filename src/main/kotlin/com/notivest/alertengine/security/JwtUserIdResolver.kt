package com.notivest.alertengine.security

import com.notivest.alertengine.exception.InvalidUserIdException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JwtUserIdResolver(
    @Value("\${JWT_USER_ID_CLAIM:claim}")
    private val userIdClaim: String,
) {
    fun requireUserId(jwt: Jwt): UUID = extractUserId(jwt) ?: throw InvalidUserIdException("JWT does not contain a valid user UUID")

    fun extractUserId(jwt: Jwt): UUID? {
        // 1) Claim configurado (full key)
        tryUuid(jwt.claims[userIdClaim] as? String)?.let { return it }

        // 2) Compat: otros namespaces que quizá hayas usado antes
        listOf(
            "user_id",
        ).forEach { k ->
            tryUuid(jwt.claims[k] as? String)?.let { return it }
        }

        // 3) Fallback: 'sub' solo si es UUID
        return tryUuid(jwt.claims["sub"] as? String)
    }

    private fun tryUuid(s: String?): UUID? = runCatching { s?.let(UUID::fromString) }.getOrNull()
}
