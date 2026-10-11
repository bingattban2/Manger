package com.manger.app.data.repository

import com.manger.app.data.local.AttendanceDb
import com.manger.app.domain.LocalSession
import com.manger.app.domain.repository.DashboardRepository
import com.manger.app.domain.repository.DashboardSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

  
class SqliteDashboardRepository(private val database: AttendanceDb) : DashboardRepository {
    override suspend fun load(session: LocalSession, includeInbox: Boolean): DashboardSnapshot =
        withContext(Dispatchers.IO) {
            try {
                DashboardSnapshot(
                    employees = database.employees(),
                    siteDetails = if (session.isAdmin) database.siteDetails() else emptyList(),
                    knownSites = if (session.isAdmin) database.sites() else emptyList(),
                    unreadInbox = database.unreadInboxCount(),
                    pendingSyncCount = database.pendingSyncCount(),
                    inboxItems = if (includeInbox) database.inbox() else emptyList()
                )
            } catch (error: IllegalStateException) {
                if (error.message == "يلزم تسجيل الدخول أولاً") DashboardSnapshot()
                else throw error
            }
        }
}
