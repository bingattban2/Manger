package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test

class AttendanceLeavePolicyTest {
    @Test fun administratorCanAddAndRemoveLeave() {
        AttendanceLeavePolicy.requireAllowed(true, "PRESENT", "LEAVE")
        AttendanceLeavePolicy.requireAllowed(true, "LEAVE", "PRESENT")
    }

    @Test fun supervisorCannotAddLeave() {
        assertFalse(AttendanceLeavePolicy.maySelectStatus(false, "LEAVE"))
        assertTrue(AttendanceLeavePolicy.maySelectStatus(false, "ABSENT"))
        assertFails { AttendanceLeavePolicy.requireAllowed(false, "PRESENT", "LEAVE") }
    }

    @Test fun supervisorCannotRemoveOrChangeCarriedForwardLeave() {
        assertFails { AttendanceLeavePolicy.requireAllowed(false, "LEAVE", "PRESENT") }
        assertFails { AttendanceLeavePolicy.requireAllowed(false, "LEAVE", "ABSENT") }
        AttendanceLeavePolicy.requireAllowed(false, "ABSENT", "PRESENT")
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); fail("Expected leave permission denial") }
        catch (_: IllegalArgumentException) { }
    }
}
