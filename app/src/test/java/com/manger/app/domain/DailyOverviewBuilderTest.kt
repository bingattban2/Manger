package com.manger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyOverviewBuilderTest {
    private val day = LocalDate.of(2026, 10, 11)
    private fun employee(id: Long, name: String, site: String, active: Boolean = true,
                         started: LocalDate = day.minusDays(30)) =
        Employee(id, id.toString().padStart(3, '0'), name, started, null, active, site)

    @Test fun totalsAgreeWithAllSitesAndMarkOverrides() {
        val people = listOf(
            employee(1, "محمد", "المكلا"), employee(2, "أحمد", "المكلا"),
            employee(3, "علي", "سيئون"), employee(4, "صالح", ""))
        val marks = mapOf((2L to day) to Attendance.ABSENT,
                          (3L to day) to Attendance.LEAVE,
                          (4L to day) to Attendance.PERMISSION)
        val overview = DailyOverviewBuilder.build(people, marks, day)
        assertEquals(4, overview.stats.total)
        assertEquals(1, overview.stats.present)
        assertEquals(1, overview.stats.absent)
        assertEquals(overview.stats.total, overview.sites.sumOf { it.stats.total })
        assertEquals(2, overview.sites.first { it.title == "المكلا" }.stats.total)
        assertEquals("غير محدد", overview.sites.last().title)
        assertEquals("أحمد", overview.employees.first().employee.name)
    }

    @Test fun newSiteAutomaticallyAppearsAndStatusesUpdate() {
        val people = listOf(employee(1, "أحمد", "المكلا"))
        val initial = DailyOverviewBuilder.build(people, emptyMap(), day)
        assertEquals(1, initial.sites.size)
        val changed = DailyOverviewBuilder.build(
            people + employee(2, "عمر", "شبام"),
            mapOf((2L to day) to Attendance.ABSENT), day)
        assertEquals(2, changed.sites.size)
        assertEquals(1, changed.stats.absent)
        assertEquals(1, changed.sites.first { it.title == "شبام" }.stats.absent)
    }

    @Test fun archivedAndNotYetHiredAreExcludedFromTodaysRoster() {
        val people = listOf(employee(1, "فعال", "المكلا"),
                            employee(2, "مؤرشف", "سيئون", active = false),
                            employee(3, "لاحق", "شبام", started = day.plusDays(2)))
        val result = DailyOverviewBuilder.build(people, emptyMap(), day)
        assertEquals(1, result.stats.total)
        assertEquals(3, result.sites.size)
        assertTrue(result.sites.filter { it.title != "المكلا" }.all { it.stats.total == 0 })
    }

    @Test fun whitespaceVariantsShareOneSite() {
        val people = listOf(employee(1, "محمد", "المكلا"), employee(2, "أحمد", " المكلا  "))
        assertEquals(1, DailyOverviewBuilder.build(people, emptyMap(), day).sites.size)
    }
}
