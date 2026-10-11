package com.manger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorDutyPolicyTest {
    @Test fun explicitPresentIsAuthorized() {
        assertTrue(SupervisorDutyPolicy.evaluate(true, true, "PRESENT").allowed)
    }
    @Test fun missingTodayMarkIsNotPresence() {
        assertEquals(DutyReason.NO_TODAY_MARK, SupervisorDutyPolicy.evaluate(true, true, null).reason)
    }
    @Test fun leaveAbsentAndPermissionBlock() {
        listOf("ABSENT", "LEAVE", "PERMISSION").forEach {
            assertFalse(SupervisorDutyPolicy.evaluate(true, true, it).allowed)
        }
    }
    @Test fun cannotClaimAnotherUnlinkedEmployee() {
        assertEquals(DutyReason.NO_EMPLOYEE_LINK, SupervisorDutyPolicy.evaluate(false, false, "PRESENT").reason)
    }
    @Test fun wrongSiteOrInactiveBlocksEvenIfPresent() {
        assertEquals(DutyReason.EMPLOYEE_INACTIVE, SupervisorDutyPolicy.evaluate(true, false, "PRESENT").reason)
    }
}
