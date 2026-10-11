package com.manger.app.domain

  
enum class LocalRole { ADMIN, SUPERVISOR }

data class LocalSession(
    val username: String,
    val displayName: String,
    val role: LocalRole,
    val site: String? = null
) {
    val isAdmin: Boolean get() = role == LocalRole.ADMIN
    init { require(isAdmin == (site == null)) { "صلاحية الحساب وموقعه غير متطابقين" } }
}

data class LocalSiteInfo(val name: String, val activeEmployees: Int, val totalEmployees: Int, val supervisors: List<String>)

data class LocalAccountInfo(val username: String, val displayName: String, val role: LocalRole, val site: String?, val employeeId: Long? = null, val enabled: Boolean = true)

  
object LocalSiteScope {
    fun employees(all: List<Employee>, session: LocalSession): List<Employee> =
        if (session.isAdmin) all else all.filter { it.site.trim() == session.site }

    fun month(all: MonthData, session: LocalSession): MonthData =
        if (session.isAdmin) all else MonthSiteFilter.select(all, session.site)

    fun mayMark(employee: Employee, session: LocalSession): Boolean =
        session.isAdmin || employee.site.trim() == session.site
}

data class LocalInboxEvent(
    val id: Long,
    val kind: String,
    val actorUsername: String,
    val actorName: String,
    val site: String,
    val title: String,
    val detail: String,
    val createdAt: String,
    val seen: Boolean
)
