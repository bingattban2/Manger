package com.manger.app.domain

object EmployeeRosterFilter {
    fun select(employees: List<Employee>, search: String, showArchived: Boolean,
               filterSite: String?, sortBySite: Boolean): List<Employee> {
        val matches = employees.filter { e ->
            (showArchived || e.active) &&
                (filterSite == null || e.site.trim() == filterSite) &&
                (e.matchesSearch(search) || e.site.contains(search, true))
        }
        return if (sortBySite) matches.sortedWith(compareBy<Employee> { it.site }
            .thenBy { EmployeeOrder.key(it.name) }) else EmployeeOrder.employees(matches)
    }
}
