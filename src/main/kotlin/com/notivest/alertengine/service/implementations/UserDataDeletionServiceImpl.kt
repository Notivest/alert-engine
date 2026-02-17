package com.notivest.alertengine.service.implementations

import com.notivest.alertengine.repositories.AlertEventRepository
import com.notivest.alertengine.repositories.AlertRuleRepository
import com.notivest.alertengine.service.interfaces.UserDataDeletionService
import com.notivest.alertengine.service.interfaces.UserDataDeletionSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserDataDeletionServiceImpl(
    private val alertEventRepository: AlertEventRepository,
    private val alertRuleRepository: AlertRuleRepository,
) : UserDataDeletionService {
    private val logger = LoggerFactory.getLogger(UserDataDeletionServiceImpl::class.java)

    @Transactional
    override fun deleteUserData(userId: UUID): UserDataDeletionSummary {
        val deletedAlertEvents = alertEventRepository.deleteByRuleUserId(userId).toLong()
        val deletedAlertRules = alertRuleRepository.deleteByUserId(userId)

        logger.info(
            "user-data-delete completed service=alert-engine userId={} deletedAlertEvents={} deletedAlertRules={}",
            userId,
            deletedAlertEvents,
            deletedAlertRules,
        )

        return UserDataDeletionSummary(
            deletedAlertEvents = deletedAlertEvents,
            deletedAlertRules = deletedAlertRules,
        )
    }
}

