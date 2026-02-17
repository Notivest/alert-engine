package com.notivest.alertengine.service.interfaces

import java.util.UUID

data class UserDataDeletionSummary(
    val deletedAlertEvents: Long,
    val deletedAlertRules: Long,
)

interface UserDataDeletionService {
    fun deleteUserData(userId: UUID): UserDataDeletionSummary
}

