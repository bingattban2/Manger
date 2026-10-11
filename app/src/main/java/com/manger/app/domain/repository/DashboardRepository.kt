package com.manger.app.domain.repository

import com.manger.app.domain.Employee
import com.manger.app.domain.LocalInboxEvent
import com.manger.app.domain.LocalSession
import com.manger.app.domain.LocalSiteInfo

  
data class DashboardSnapshot(
    val employees: List<Employee> = emptyList(),
    val siteDetails: List<LocalSiteInfo> = emptyList(),
    val knownSites: List<String> = emptyList(),
    val unreadInbox: Int = 0,
    val pendingSyncCount: Int = 0,
    val inboxItems: List<LocalInboxEvent> = emptyList()
)

interface DashboardRepository {
    suspend fun load(session: LocalSession, includeInbox: Boolean): DashboardSnapshot
}
