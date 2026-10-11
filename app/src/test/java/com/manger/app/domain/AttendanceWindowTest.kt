package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class AttendanceWindowTest {
    private fun at(hour: Int, minute: Int = 0) = LocalTime.of(hour, minute)

    @Test fun windowInSameDay() {
        val policy = AttendanceWindow(true, at(8), at(15))
        assertFalse(policy.allows(at(7, 59)))
        assertTrue(policy.allows(at(8)))
        assertTrue(policy.allows(at(14, 59)))
        assertFalse(policy.allows(at(15)))
    }

    @Test fun crossesMidnight() {
        val policy = AttendanceWindow(true, at(21), at(3))
        assertTrue(policy.allows(at(23, 59)))
        assertTrue(policy.allows(at(2, 59)))
        assertFalse(policy.allows(at(3)))
        assertFalse(policy.allows(at(15)))
    }

    @Test fun disabledWindowAndPersistence() {
        val disabled = AttendanceWindow()
        assertTrue(disabled.allows(at(0)))
        assertTrue(disabled.allows(at(23, 59)))
        assertEquals(disabled, AttendanceWindow.parse(disabled.serialize()))
        val policy = AttendanceWindow(true, at(6, 30), at(17, 45))
        assertEquals(policy, AttendanceWindow.parse(policy.serialize()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyWindowIsRejected() {
        AttendanceWindow(true, at(8), at(8))
    }
}
