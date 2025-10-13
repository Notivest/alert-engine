package com.notivest.alertengine.controllers

import com.notivest.alertengine.controllers.dto.alertrule.request.CreateAlertRuleRequest
import com.notivest.alertengine.controllers.dto.alertrule.request.GetAlertQuery
import com.notivest.alertengine.controllers.dto.alertrule.request.UpdateAlertRuleRequest
import com.notivest.alertengine.controllers.dto.alertrule.response.AlertRuleResponse
import com.notivest.alertengine.controllers.dto.alertrule.toResponse
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.security.JwtUserIdResolver
import com.notivest.alertengine.service.interfaces.AlertRuleService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/alerts")
class AlertRuleController(
    private val alertRuleService: AlertRuleService,
    private val userIdResolver: JwtUserIdResolver,
) {

    @GetMapping
    fun list(
        query: GetAlertQuery,
        pageable: Pageable,
        @AuthenticationPrincipal auth: Jwt
    ): Page<AlertRuleResponse> {
        val userId = userIdResolver.requireUserId(auth)
        println(userId)
        return alertRuleService.list(userId, query, pageable).map { it.toResponse() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody body: CreateAlertRuleRequest,
        @AuthenticationPrincipal auth: Jwt
    ): AlertRuleResponse {
        val userId = userIdResolver.requireUserId(auth)
        return alertRuleService.create(userId, body).toResponse()
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody body: UpdateAlertRuleRequest,
        @AuthenticationPrincipal auth: Jwt
    ): AlertRuleResponse {
        val userId = userIdResolver.requireUserId(auth)
        return alertRuleService.update(userId, id, body).toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal auth: Jwt
    ) {
        val userId = userIdResolver.requireUserId(auth)
        alertRuleService.setStatus(userId, id, RuleStatus.DISABLED)
    }
}