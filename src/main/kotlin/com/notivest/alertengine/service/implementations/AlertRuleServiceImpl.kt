package com.notivest.alertengine.service.implementations

import com.notivest.alertengine.controllers.dto.alertrule.request.CreateAlertRuleRequest
import com.notivest.alertengine.controllers.dto.alertrule.request.GetAlertQuery
import com.notivest.alertengine.controllers.dto.alertrule.request.UpdateAlertRuleRequest
import com.notivest.alertengine.exception.ForbiddenOperationException
import com.notivest.alertengine.exception.ResourceNotFoundException
import com.notivest.alertengine.models.AlertRule
import com.notivest.alertengine.models.enums.RuleStatus
import com.notivest.alertengine.repositories.AlertRuleRepository
import com.notivest.alertengine.repositories.spec.AlertRuleSpecs
import com.notivest.alertengine.service.interfaces.AlertRuleService
import com.notivest.alertengine.validation.AlertParamsValidator
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID
import org.springframework.transaction.annotation.Transactional


@Service
class AlertRuleServiceImpl(
    private val repository: AlertRuleRepository,
    private val validator: AlertParamsValidator
) : AlertRuleService {
    override fun list(
        userId: UUID,
        query: GetAlertQuery,
        pageable: Pageable
    ): Page<AlertRule> {
        val spec = Specification.where(AlertRuleSpecs.byUser(userId))
            .and(AlertRuleSpecs.status(query.status))
            .and(AlertRuleSpecs.symbol(query.symbol))
            .and(AlertRuleSpecs.kind(query.kind))
            .and(AlertRuleSpecs.timeframe(query.timeframe))

        return repository.findAll(spec, pageable)
    }

    override fun create(
        userId: UUID,
        command: CreateAlertRuleRequest
    ): AlertRule {
        validator.validate(command.kind, command.params)

        val entity = AlertRule(
            userId = userId,
            symbol = command.symbol,
            kind = command.kind,
            params = command.params,
            timeframe = command.timeframe,
            status = command.status ?: RuleStatus.ACTIVE,
            debounceTime = command.debounceSeconds?.let { Duration.ofSeconds(it) }
        )

        return repository.saveAndFlush(entity)
    }

    override fun update(
        userId: UUID,
        alertId: UUID,
        command: UpdateAlertRuleRequest
    ): AlertRule {
        val rule = findOwnedOrThrow(userId, alertId)

        // si cambian params, revalidar contra el schema del kind actual
        command.params?.let { newParams ->
            validator.validate(rule.kind, newParams)
            rule.params = newParams
        }

        command.status?.let { rule.status = it }

        command.debounceSeconds?.let { secs ->
            rule.debounceTime = Duration.ofSeconds(secs)
        }

        return repository.saveAndFlush(rule)
    }

    @Transactional
    override fun setStatus(userId: UUID, alertId: UUID, status: RuleStatus): AlertRule {
        val rule = findOwnedOrThrow(userId, alertId)
        if (rule.status != status) {
            rule.status = status
            repository.save(rule)
        }
        return rule
    }

    override fun get(userId: UUID, alertId: UUID): AlertRule {
        return findOwnedOrThrow(userId, alertId)
    }

    private fun findOwnedOrThrow(userId: UUID, id: UUID): AlertRule {
        val rule = repository.findById(id).orElseThrow {
            ResourceNotFoundException("Alert rule $id not found")
        }
        if (rule.userId != userId) {
            throw ForbiddenOperationException("You don't own alert rule $id")
        }
        return rule
    }


}