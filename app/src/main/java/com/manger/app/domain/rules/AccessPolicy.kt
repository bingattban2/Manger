package com.manger.app.domain

enum class AppPermission {
    VIEW_ALL_SITES, VIEW_OWN_SITE, MANAGE_ALL_ROSTERS, MANAGE_SITES,
    MANAGE_SUPERVISORS, MANAGE_COLUMNS, MANAGE_ATTENDANCE_WINDOW,
    MANAGE_PERMISSIONS, EDIT_ATTENDANCE, READ_REPORTS, VIEW_ARCHIVE,
    MANAGE_BACKUP
}

object AccessPolicy {
    private val supervisor = setOf(AppPermission.VIEW_OWN_SITE, AppPermission.EDIT_ATTENDANCE,
        AppPermission.READ_REPORTS, AppPermission.VIEW_ARCHIVE)
    fun has(role: LocalRole, permission: AppPermission): Boolean =
        role == LocalRole.ADMIN || permission in supervisor

    fun mayEditAttendance(role: LocalRole, assignedSiteId: String?, employeeSiteId: String?,
                          accountEnabled: Boolean, presentTodayExplicitly: Boolean,
                          insideServerWindow: Boolean): Boolean {
        if (role == LocalRole.ADMIN) return accountEnabled
        return role == LocalRole.SUPERVISOR && accountEnabled && presentTodayExplicitly &&
            insideServerWindow && assignedSiteId != null && assignedSiteId == employeeSiteId
    }
}
