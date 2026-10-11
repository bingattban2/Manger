package com.manger.app.domain.repository

import com.manger.app.domain.LocalSession
import com.manger.app.domain.SupervisorDutyDecision

  
interface AuthenticationRepository {
    suspend fun authenticate(username: String, password: String): LocalSession?
    suspend fun checkDuty(account: LocalSession): SupervisorDutyDecision
    suspend fun requestDutyConfirmation(account: LocalSession)
}
