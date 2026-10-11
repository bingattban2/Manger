package com.manger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class AttendanceBatchPlannerTest {
    private val day = LocalDate.of(2026, 10, 11)
    private val people = listOf(
        Employee(1L, "001", "أحمد", day.minusMonths(1), null, true, "المكلا"),
        Employee(2L, "002", "محمد", day.minusMonths(1), null, true, "المكلا"),
        Employee(3L, "003", "صالح", day.minusMonths(1), null, true, "سيئون")
    )

    @Test fun appliesDifferentStatusesInOneApproval() {
        val plan = AttendanceBatchPlanner.plan(listOf(
            AttendanceBatch(setOf(1L, 2L), Attendance.ABSENT, day, day),
            AttendanceBatch(setOf(3L), Attendance.LEAVE, day, day.plusDays(1))
        ), people)
        assertEquals(4, plan.size)
        assertEquals(Attendance.ABSENT, plan[1L to day])
        assertEquals(Attendance.ABSENT, plan[2L to day])
        assertEquals(Attendance.LEAVE, plan[3L to day.plusDays(1)])
    }

    @Test fun lastGroupWinsIfSameDayAndEmployeeOverlap() {
        val plan = AttendanceBatchPlanner.plan(listOf(
            AttendanceBatch(setOf(1L, 2L), Attendance.ABSENT, day, day),
            AttendanceBatch(setOf(2L), Attendance.PERMISSION, day, day)
        ), people)
        assertEquals(2, plan.size)
        assertEquals(Attendance.ABSENT, plan[1L to day])
        assertEquals(Attendance.PERMISSION, plan[2L to day])
    }

    @Test fun rejectsWrongDateBeforeSavingAnyChanges() {
        assertThrows(IllegalArgumentException::class.java) {
            AttendanceBatchPlanner.plan(listOf(
                AttendanceBatch(setOf(1L), Attendance.ABSENT, day.plusDays(1), day)
            ), people)
        }
    }
}
