package com.notivest.alertengine.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import kotlin.collections.plusAssign

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authenticationErrorHandler: AuthenticationErrorHandler,
) {
    @Value("\${spring.security.oauth2.resourceserver.jwt.audience:}")
    private val audience: String = ""

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val issuer: String = ""

    @Bean
    @Profile("!auth")
    fun openChain(http: HttpSecurity): SecurityFilterChain {
        return http.csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    @Bean
    @Profile("auth")
    fun securedChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/docs/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/openapi/**",
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .jwt { jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        jwt.decoder(jwtDecoder)
                    }
                    .authenticationEntryPoint(authenticationErrorHandler)
            }
            .build()

    /** Converter: SCOPE_* desde 'scope' o 'permissions'; ROLE_* desde claim de roles */
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val scopesConv =
            JwtGrantedAuthoritiesConverter().apply {
                setAuthoritiesClaimName("scope") // "scope" (string)
                setAuthorityPrefix("SCOPE_")
            }

        val converter =
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter { jwt ->
                    val out = mutableListOf<GrantedAuthority>()

                    // 1) scope (string)
                    out += scopesConv.convert(jwt)?.toList().orEmpty()

                    // 2) permissions (array) de Auth0 (requiere RBAC + "Add Permissions in the Access Token")
                    val perms = (jwt.claims["permissions"] as? Collection<*>)?.filterIsInstance<String>().orEmpty()
                    out += perms.map { SimpleGrantedAuthority("SCOPE_$it") }

                    // (opcional) si todavía te llega algún claim de roles, lo puedes conservar:
                    val rolesClaim = jwt.claims["roles"] ?: jwt.claims["user_roles"]
                    val roles =
                        when (rolesClaim) {
                            is Collection<*> -> rolesClaim.filterIsInstance<String>()
                            is Array<*> -> rolesClaim.filterIsInstance<String>()
                            is String -> rolesClaim.split(',').map { it.trim() }
                            else -> emptyList()
                        }
                    out += roles.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") } // ya no lo usas, pero no molesta

                    out
                }
            }
        return converter
    }

    /** Decoder con validación de issuer y audience (string o array) */
    @Bean
    @Profile("auth")
    fun jwtDecoder(): JwtDecoder {
        require(issuer.isNotBlank()) { "JWT issuer URI must be configured" }
        val normalizedIssuer = if (issuer.endsWith("/")) issuer else "$issuer/"

        // Asegurá el tipo concreto para evitar problemas de inferencia
        val decoder: NimbusJwtDecoder = JwtDecoders.fromIssuerLocation(normalizedIssuer)

        val withIssuer: OAuth2TokenValidator<Jwt> =
            JwtValidators.createDefaultWithIssuer(normalizedIssuer)

        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>(withIssuer)

        if (audience.isNotBlank()) {
            validators +=
                OAuth2TokenValidator<Jwt> { jwt ->
                    val auds =
                        when (val aud = jwt.claims["aud"]) {
                            is String -> listOf(aud)
                            is Collection<*> -> aud.filterIsInstance<String>()
                            else -> emptyList()
                        }
                    if (auds.contains(audience)) {
                        OAuth2TokenValidatorResult.success()
                    } else {
                        OAuth2TokenValidatorResult.failure(
                            OAuth2Error("invalid_audience", "Invalid audience: expected '$audience'", null),
                        )
                    }
                }
        }

        // IMPORTANTE: pasá vararg y el tipo <Jwt> explícito
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator<Jwt>(*validators.toTypedArray()),
        )

        return decoder // se puede devolver como JwtDecoder (interface)
    }
}
