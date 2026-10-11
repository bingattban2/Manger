package com.manger.app.application

import com.manger.app.application.auth.SignInResult
import com.manger.app.application.auth.SignInUseCase
import com.manger.app.domain.*
import com.manger.app.domain.repository.AuthenticationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {
    @Test fun `supervisor cannot enter without a present duty mark`() = runBlocking {
        val session = LocalSession("s1", "مشرف", LocalRole.SUPERVISOR, "الموقع الأول")
        val repo = object : AuthenticationRepository {
            override suspend fun authenticate(username: String, password: String) = session
            override suspend fun checkDuty(account: LocalSession) = SupervisorDutyDecision(DutyReason.NO_TODAY_MARK)
            override suspend fun requestDutyConfirmation(account: LocalSession) = Unit
        }
        assertTrue(SignInUseCase(repo).signIn("s1", "password") is SignInResult.DutyBlocked)
    }
}
