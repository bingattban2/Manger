package com.manger.app.application.auth

import com.manger.app.domain.LocalSession
import com.manger.app.domain.SupervisorDutyDecision
import com.manger.app.domain.repository.AuthenticationRepository

sealed interface SignInResult {
    data object InvalidCredentials : SignInResult
    data class Granted(val account: LocalSession) : SignInResult
    data class DutyBlocked(val account: LocalSession, val decision: SupervisorDutyDecision) : SignInResult
}

  
class SignInUseCase(private val auth: AuthenticationRepository) {
    suspend fun signIn(username: String, password: String): SignInResult {
        val account = auth.authenticate(username, password) ?: return SignInResult.InvalidCredentials
        val decision = auth.checkDuty(account)
        return if (decision.allowed) SignInResult.Granted(account)
        else SignInResult.DutyBlocked(account, decision)
    }

    suspend fun recheck(account: LocalSession): SupervisorDutyDecision = auth.checkDuty(account)
    suspend fun requestDutyConfirmation(account: LocalSession) = auth.requestDutyConfirmation(account)
}
