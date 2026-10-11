package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodReportBuilderTest {
    private val day = LocalDate.of(2026, 10, 11)
    private val employee = Employee(9, "009", "فرد اختبار", LocalDate.of(2026, 9, 29), null, true, "موقع أ")

    @Test fun groupsSavedAndDefaultStatusesAcrossMonths() {
        val sep = MonthData(YearMonth.of(2026, 9), listOf(employee),
            mapOf((9L to LocalDate.of(2026,9,30)) to Attendance.ABSENT))
        val oct = MonthData(YearMonth.of(2026,10), listOf(employee),
            mapOf((9L to day) to Attendance.PERMISSION))
        val report = PeriodReportBuilder.create(listOf(employee), LocalDate.of(2026,9,29), day, day) { m ->
            if (m == sep.month) sep else oct
        }
        val row = report.rows.single()
        assertEquals(13, row.days.size)
        assertEquals(1, row.counts["ABSENT"])
        assertEquals(1, row.counts["PERMISSION"])
        assertEquals(11, row.counts["PRESENT"])
        assertEquals(2, row.byMonth.size)
    }

    @Test fun missingArchiveNeverCountsAsPresent() {
        val current = MonthData(YearMonth.of(2026,10), listOf(employee), emptyMap())
        val report = PeriodReportBuilder.create(listOf(employee), LocalDate.of(2026,9,29), day, day) { m ->
            if (m == current.month) current else null
        }
        assertEquals(listOf(YearMonth.of(2026,9)), report.unavailableMonths)
        assertEquals(11, report.rows.single().days.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blocksFutureDays() {
        PeriodReportBuilder.create(listOf(employee), day, day.plusDays(1), day) { null }
    }
}
