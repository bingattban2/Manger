package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthSiteFilterTest {
    private val joined = LocalDate.of(2025, 1, 1)
    private val date = LocalDate.of(2026, 10, 11)
    private val employees = listOf(
        Employee(1, "001", "أحمد", joined, null, true, "المكلا"),
        Employee(2, "002", "بدر", joined, null, true, "سيئون"),
        Employee(3, "003", "خالد", joined, null, true, ""),
        Employee(4, "004", "علي", joined, null, true, " المكلا ")
    )
    private val month = MonthData(YearMonth.of(2026, 10), employees,
        mapOf((1L to date) to Attendance.ABSENT,
            (2L to date) to Attendance.LEAVE,
            (3L to date) to Attendance.PERMISSION,
            (4L to date) to Attendance.ABSENT))

    @Test fun allSitesReturnOriginalData() {
        assertEquals(month, MonthSiteFilter.select(month, null))
        assertEquals(4, MonthSiteFilter.select(month, null).employees.size)
    }

    @Test fun filteringKeepsOnlySelectedEmployeesAndTheirMarks() {
        val selected = MonthSiteFilter.select(month, "المكلا")
        assertEquals(listOf(1L, 4L), selected.employees.map { it.id })
        assertEquals(setOf(1L, 4L), selected.marks.keys.map { it.first }.toSet())
        assertEquals(2, selected.marks.size)
        assertEquals(4, month.employees.size)
    }

    @Test fun employeesWithoutAssignedSiteCanBeSelected() {
        assertEquals(listOf(3L), MonthSiteFilter.select(month, "").employees.map { it.id })
        assertEquals("بدون موقع", MonthSiteFilter.label(""))
        assertEquals("جميع المواقع", MonthSiteFilter.label(null))
    }

    @Test fun sitesAreUniqueAndExportNamesAreSafe() {
        assertEquals(setOf("المكلا", "سيئون", ""), MonthSiteFilter.sites(month).toSet())
        assertEquals("", MonthSiteFilter.sites(month).last())
        assertEquals("موقع_الشرق", MonthSiteFilter.filePart("موقع/الشرق"))
    }
}
