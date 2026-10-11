package com.manger.app.domain

import java.text.Collator
import java.time.LocalDate
import java.util.Locale

  
data class DailyEmployee(val employee: Employee, val status: Attendance)

data class DailySite(
    val key: String,
    val title: String,
    val employees: List<DailyEmployee>,
    val stats: DayStats
)

data class DailyOverview(
    val employees: List<DailyEmployee>,
    val stats: DayStats,
    val sites: List<DailySite>
)

object DailyOverviewBuilder {
    private val arabic = Locale.forLanguageTag("ar")

    private fun stats(rows: List<DailyEmployee>) = DayStats(
        total = rows.size,
        present = rows.count { it.status.name == "PRESENT" },
        absent = rows.count { it.status.name == "ABSENT" },
        leave = rows.count { it.status.name == "LEAVE" },
        permission = rows.count { it.status.name == "PERMISSION" },
        counts = rows.groupingBy { it.status.name }.eachCount()
    )

    fun build(
        employees: List<Employee>,
        marks: Map<Pair<Long, LocalDate>, Attendance>,
        day: LocalDate
    ): DailyOverview {
        val sorted = EmployeeOrder.employees(employees)
        val todayRows = sorted.filter { it.active && it.employedOn(day) }
            .map { DailyEmployee(it, marks[it.id to day] ?: Attendance.fromStored("PRESENT")) }

        val namesByKey = linkedMapOf<String, String>()
        for (employee in sorted) {
            val title = EmployeeOrder.clean(employee.site).ifBlank { "غير محدد" }
            val key = if (employee.site.isBlank()) "unassigned" else "site:${EmployeeOrder.key(title)}"
            if (employee.site.isNotBlank()) namesByKey.putIfAbsent(key, title)
        }
        val rowsByKey = todayRows.groupBy { row ->
            val site = EmployeeOrder.clean(row.employee.site)
            if (site.isBlank()) "unassigned" else "site:${EmployeeOrder.key(site)}"
        }
        val collator = Collator.getInstance(arabic).apply { strength = Collator.PRIMARY }
        val sites = namesByKey.entries.sortedWith { a, b ->
            when {
                a.key == "unassigned" && b.key != "unassigned" -> 1
                a.key != "unassigned" && b.key == "unassigned" -> -1
                else -> collator.compare(a.value, b.value)
            }
        }.map { (key, title) ->
            val rows = rowsByKey[key].orEmpty()
            DailySite(key, title, rows, stats(rows))
        }
        return DailyOverview(todayRows, stats(todayRows), sites)
    }
}
