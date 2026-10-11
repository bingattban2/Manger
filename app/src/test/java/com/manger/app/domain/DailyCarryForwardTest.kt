package com.manger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailyCarryForwardTest {
    @Test fun copiesPreviousDayWithoutChangingSavedRows() {
        val previous = LocalDate.of(2026, 10, 11)
        val following = previous.plusDays(1)
        val people = (1L..21L).toList()
        val saved = buildMap<Pair<Long, LocalDate>, Attendance> {
            (1L..15L).forEach { put(it to previous, Attendance.PRESENT) }
            (16L..18L).forEach { put(it to previous, Attendance.ABSENT) }
            (19L..20L).forEach { put(it to previous, Attendance.LEAVE) }
            put(21L to previous, Attendance.PERMISSION)
        }
        val resolved = DailyCarryForward.resolve(people, previous, following, following, emptyMap(), saved)
        val counts = people.groupingBy { resolved[it to following]?.name ?: "PRESENT" }.eachCount()
        assertEquals(15, counts["PRESENT"])
        assertEquals(3, counts["ABSENT"])
        assertEquals(2, counts["LEAVE"])
        assertEquals(1, counts["PERMISSION"])
        assertFalse(saved.containsKey(16L to following)) // no fabricated DB write
    }

    @Test fun explicitEditOverridesOnlyOneEmployee() {
        val day = LocalDate.of(2026, 10, 12)
        val previous = mapOf(1L to Attendance.ABSENT, 2L to Attendance.LEAVE)
        val result = DailyCarryForward.resolve(listOf(1L, 2L), day, day, day, previous,
            mapOf((1L to day) to Attendance.PRESENT))
        assertEquals("PRESENT", result[1L to day]?.name)
        assertEquals("LEAVE", result[2L to day]?.name)
    }

    @Test fun rollsAcrossMonthsButNeverIntoFuture() {
        val start = LocalDate.of(2026, 11, 1)
        val result = DailyCarryForward.resolve(listOf(1L), start, start.plusDays(1), start,
            mapOf(1L to Attendance.ABSENT), emptyMap())
        assertEquals("ABSENT", result[1L to start]?.name)
        assertNull(result[1L to start.plusDays(1)])
    }
}
