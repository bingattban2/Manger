package com.manger.app.domain

import java.time.LocalDate

object DailyCarryForward {
    fun resolve(
        employeeIds: Collection<Long>,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
        prior: Map<Long, Attendance>,
        explicit: Map<Pair<Long, LocalDate>, Attendance>
    ): Map<Pair<Long, LocalDate>, Attendance> {
        require(!end.isBefore(start)) { "نطاق التواريخ غير صالح" }
        
        require(end.toEpochDay() - start.toEpochDay() <= 366L) { "يجب قراءة الحالات على فترات سنوية أو أقصر" }
        val answer = HashMap<Pair<Long, LocalDate>, Attendance>(explicit.size + employeeIds.size)
        for (id in employeeIds) {
            var state = prior[id] ?: Attendance.PRESENT
            var day = start
            while (!day.isAfter(end)) {
                val key = id to day
                val manual = explicit[key]
                if (manual != null) state = manual
                if (manual != null || (!day.isAfter(today) && state.name != "PRESENT")) {
                    answer[key] = state
                }
                day = day.plusDays(1)
            }
        }
        return answer
    }
}
