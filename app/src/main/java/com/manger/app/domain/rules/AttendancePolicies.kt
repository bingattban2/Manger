package com.manger.app.domain

import java.time.LocalDate

  
object AttendanceBatchPlanner {
    fun plan(groups: List<AttendanceBatch>, employees: List<Employee>): Map<Pair<Long, LocalDate>, Attendance> {
        require(groups.isNotEmpty()) { "لا توجد مجموعات جاهزة للاعتماد" }
        val employeesById = employees.associateBy { it.id }
        val changes = linkedMapOf<Pair<Long, LocalDate>, Attendance>()
        for (group in groups) {
            require(group.employeeIds.isNotEmpty()) { "اختر فرداً واحداً على الأقل" }
            require(!group.end.isBefore(group.start)) { "تاريخ النهاية يسبق البداية" }
            require(group.end.toEpochDay() - group.start.toEpochDay() <= 3660L) { "النطاق كبير جداً (الحد 3661 يوماً)" }
            for (id in group.employeeIds) {
                val employee = employeesById[id] ?: throw IllegalArgumentException("بعض الافراد غير موجودين")
                var day = group.start
                while (!day.isAfter(group.end)) {
                    require(employee.employedOn(day)) { "التاريخ خارج فترة عمل: ${employee.name}" }
                    changes[id to day] = group.status
                    day = day.plusDays(1)
                }
            }
        }
        return changes
    }
}


object AttendanceRules {
    fun defaultFor(day: LocalDate, today: LocalDate, employed: Boolean, saved: Attendance?): Attendance? {
        if (!employed) return null
        return if (day.isAfter(today) && saved == null) null else saved ?: Attendance.fromStored("PRESENT")
    }
    fun count(employees: List<Employee>, marks: Map<Pair<Long, LocalDate>, Attendance>, day: LocalDate): DayStats {
        val valid = employees.filter { it.active && it.employedOn(day) }
        val statuses = valid.map { marks[it.id to day] ?: Attendance.fromStored("PRESENT") }
        val counts = statuses.groupingBy { it.name }.eachCount()
        return DayStats(valid.size, counts["PRESENT"] ?: 0, counts["ABSENT"] ?: 0,
            counts["LEAVE"] ?: 0, counts["PERMISSION"] ?: 0, counts)
    }
}
