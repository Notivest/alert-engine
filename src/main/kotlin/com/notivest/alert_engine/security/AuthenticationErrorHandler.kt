package com.notivest.alert_engine.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AuthenticationErrorHandler(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    private val logger = LoggerFactory.getLogger(AuthenticationErrorHandler::class.java)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = createErrorResponse(authException)

        // Log error for debugging
        logger.warn("Authentication failed: ${errorResponse.message}", authException)

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
        response.writer.flush()
    }

    private fun createErrorResponse(ex: AuthenticationException): ErrorResponse {
        return when (ex) {
            is OAuth2AuthenticationException -> {
                when {
                    ex.error.errorCode == "invalid_token" ->
                        ErrorResponse(
                            error = "invalid_token",
                            message = "The provided JWT is invalid",
                            details = "Make sure the token is well-formed and not expired",
                        )
                    ex.error.errorCode == "insufficient_scope" ->
                        ErrorResponse(
                            error = "insufficient_scope",
                            message = "The token lacks the required permissions",
                            details = "Additional permissions are required to access this resource",
                        )
                    else ->
                        ErrorResponse(
                            error = "authentication_failed",
                            message = "OAuth2 authentication error",
                            details = ex.error.description ?: "Invalid or expired token",
                        )
                }
            }

            else -> {
                when {
                    ex.message?.contains("JWT") == true ->
                        ErrorResponse(
                            error = "jwt_error",
                            message = "Error processing the JWT token",
                            details = "Malformed, expired, or invalid token",
                        )
                    ex.message?.contains("Bearer") == true ->
                        ErrorResponse(
                            error = "missing_token",
                            message = "Authorization token required",
                            details = "Include the header 'Authorization: Bearer <token>'",
                        )
                    else ->
                        ErrorResponse(
                            error = "unauthorized",
                            message = "Unauthorized access",
                            details = "Valid authentication is required to access this resource",
                        )
                }
            }
        }
    }

    data class ErrorResponse(
        val error: String,
        val message: String,
        val details: String,
        val timestamp: String = Instant.now().toString(),
        val status: Int = 401,
    )
}
