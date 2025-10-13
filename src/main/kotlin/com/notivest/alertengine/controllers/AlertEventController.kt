package com.notivest.alertengine.controllers

import com.notivest.alertengine.controllers.dto.alertevent.AlertEventResponse
import com.notivest.alertengine.controllers.dto.alertevent.toResponse
import com.notivest.alertengine.security.JwtUserIdResolver
import com.notivest.alertengine.service.interfaces.AlertEventService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/alert-events")
class AlertEventController(
    private val alertEventService: AlertEventService,
    private val userIdResolver: JwtUserIdResolver,
) {

    @GetMapping
    fun list(
        pageable: Pageable,
        @AuthenticationPrincipal auth: Jwt,
    ): Page<AlertEventResponse> {
        val userId = userIdResolver.requireUserId(auth)
        return alertEventService.list(userId, pageable).map { it.toResponse() }
    }
}