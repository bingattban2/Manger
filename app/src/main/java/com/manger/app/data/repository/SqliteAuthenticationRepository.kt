package com.manger.app.data.repository

import com.manger.app.data.local.AttendanceDb
import com.manger.app.domain.LocalSession
import com.manger.app.domain.SupervisorDutyDecision
import com.manger.app.domain.repository.AuthenticationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteAuthenticationRepository(private val db: AttendanceDb) : AuthenticationRepository {
    override suspend fun authenticate(username: String, password: String): LocalSession? =
        withContext(Dispatchers.IO) { db.login(username, password) }

    override suspend fun checkDuty(account: LocalSession): SupervisorDutyDecision =
        withContext(Dispatchers.IO) { db.supervisorDuty(account) }

    override suspend fun requestDutyConfirmation(account: LocalSession) =
        withContext(Dispatchers.IO) { db.requestDutyConfirmation(account) }
}
