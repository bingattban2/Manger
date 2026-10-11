package com.manger.app.domain

object ReportEmployeeFilter {
    fun select(people: List<Employee>, site: String?, query: String): List<Employee> =
        people.filter { (site == null || it.site.trim() == site) && it.matchesSearch(query) }
}
