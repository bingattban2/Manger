package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LocalSiteScopeTest {
    private val month = YearMonth.of(2026, 10)
    private val first = Employee(1L, "001", "فرد 1", LocalDate.of(2025, 1, 1), null, true, "موقع ألف")
    private val second = Employee(2L, "002", "فرد 2", LocalDate.of(2025, 1, 1), null, true, "موقع باء")
    private val admin = LocalSession("admin", "الإدارة", LocalRole.ADMIN)
    private val supervisor = LocalSession("su1", "مشرف", LocalRole.SUPERVISOR, "موقع ألف")

    @Test fun restrictEmployeesToAssignedSite() {
        assertEquals(listOf(first, second), LocalSiteScope.employees(listOf(first, second), admin))
        assertEquals(listOf(first), LocalSiteScope.employees(listOf(first, second), supervisor))
        assertTrue(LocalSiteScope.mayMark(first, supervisor))
        assertFalse(LocalSiteScope.mayMark(second, supervisor))
    }

    @Test fun restrictCurrentAndArchivedReportsWithoutLeakingMarks() {
        val day = month.atDay(5)
        val original = MonthData(month, listOf(first, second), mapOf(
            (1L to day) to Attendance.ABSENT,
            (2L to day) to Attendance.LEAVE
        ))
        val scoped = LocalSiteScope.month(original, supervisor)
        assertEquals(listOf(first), scoped.employees)
        assertEquals(setOf(1L to day), scoped.marks.keys)
        assertEquals(original, LocalSiteScope.month(original, admin))
    }

    @Test(expected = IllegalArgumentException::class)
    fun supervisorsMustHaveAssignedSites() {
        LocalSession("su2", "مشرف", LocalRole.SUPERVISOR, null)
    }
}
