package com.manger.app.domain

import java.time.LocalDate
import java.time.YearMonth

data class Attendance(val name: String, val symbol: String, val label: String, val argb: Long) {
    companion object {
        val PRESENT = Attendance("PRESENT", "م", "موجود", 0xFF21885AL)
        val ABSENT = Attendance("ABSENT", "غ", "غياب", 0xFFCA4350L)
        val LEAVE = Attendance("LEAVE", "ج", "إجازة", 0xFF3477CBL)
        val PERMISSION = Attendance("PERMISSION", "إ", "إذن", 0xFFBA8317L)
        val defaults = listOf(PRESENT, ABSENT, LEAVE, PERMISSION)
        @Volatile private var catalog: List<Attendance> = defaults
        val entries: List<Attendance> get() = catalog
        fun configure(items: List<Attendance>) {
            require(items.map { it.name }.distinct().size == items.size)
            require(items.any { it.name == "PRESENT" })
            catalog = items.toList()
        }
        fun fromStored(value: String?): Attendance {
            if (value.isNullOrBlank()) return entries.first { it.name == "PRESENT" }
            return entries.firstOrNull { it.name == value }
                ?: Attendance(value, "?", "حالة غير معروفة", 0xFF757575L)
        }
    }
}

data class Employee(
    val id: Long,
    val code: String,
    val name: String,
    val started: LocalDate,
    val ended: LocalDate?,
    val active: Boolean,
    val site: String = "",
    val rosterNumber: String = "" 
) {
    fun matchesSearch(input: String): Boolean {
        val term = RosterNumbers.searchKey(input)
        return term.isBlank() || listOf(name, code, rosterNumber).any {
            RosterNumbers.searchKey(it).contains(term)
        }
    }
    fun employedOn(day: LocalDate): Boolean = !day.isBefore(started) && (ended == null || !day.isAfter(ended))
    fun overlaps(month: YearMonth): Boolean = !started.isAfter(month.atEndOfMonth()) && (ended == null || !ended.isBefore(month.atDay(1)))
}

  
data class AttendanceBatch(
    val employeeIds: Set<Long>,
    val status: Attendance,
    val start: LocalDate,
    val end: LocalDate
)

data class ImportedEmployee(val name: String, val site: String = "", val rosterNumber: String = "", val sheet: String = "")
data class ImportSummary(val added: Int, val sitesFilled: Int, val updated: Int = 0)
data class ImportCheck(val added: Int, val updated: Int, val unchanged: Int, val warnings: List<String>)
data class Mark(val employeeId: Long, val day: LocalDate, val status: Attendance)
data class Audit(val id: Long, val employeeId: Long, val day: String, val before: String, val after: String, val changed: String)
data class DayStats(
    val total: Int, val present: Int, val absent: Int, val leave: Int, val permission: Int,
    val counts: Map<String, Int> = emptyMap()
) {
    fun count(status: Attendance): Int = counts[status.name] ?: when (status.name) {
        "PRESENT" -> present; "ABSENT" -> absent; "LEAVE" -> leave; "PERMISSION" -> permission
        else -> 0
    }
}

data class EmployeeMonthReport(val month: YearMonth, val totals: Map<String, Int>, val days: Int)

data class MonthData(val month: YearMonth, val employees: List<Employee>, val marks: Map<Pair<Long, LocalDate>, Attendance>,
    val columnValues: Map<Pair<Long,String>, String> = emptyMap()) {
    fun status(employee: Employee, day: LocalDate, today: LocalDate = LocalDate.now()): Attendance? {
        if (!employee.employedOn(day)) return null
        val saved = marks[employee.id to day]
        return if (day.isAfter(today) && saved == null) null else saved ?: Attendance.fromStored("PRESENT")
    }
}

