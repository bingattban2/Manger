package com.manger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterAndSupervisorRulesTest {
    @Test fun defaultColumnsHaveRequiredOrder() {
        assertEquals(listOf("ordinal", "roster", "name", "site"), RosterColumnLayout.defaults.map { it.key })
    }

    @Test fun adminColumnOrderSurvivesSerialization() {
        val custom = listOf(
            RosterColumn("site", "المنطقة"), RosterColumn("roster", "الرقم (ع)"),
            RosterColumn("name", "الإسم"), RosterColumn("ordinal", "م")
        )
        assertEquals(custom, RosterColumnLayout.decode(RosterColumnLayout.encode(custom)))
    }

    @Test fun supervisorPasswordRequiresThreeDigitsAndSymbol() {
        assertTrue(SupervisorPasswordPolicy.isValid("ab123!"))
        assertFalse(SupervisorPasswordPolicy.isValid("abc123"))
        assertFalse(SupervisorPasswordPolicy.isValid("abc12!"))
        assertFalse(SupervisorPasswordPolicy.isValid("ab12!"))
        assertFalse(SupervisorPasswordPolicy.isValid("ab123 !"))
    }

    @Test fun supervisorPresenceCannotBeInferredFromYesterday() {
        assertTrue(SupervisorDutyPolicy.evaluate(true, true, "PRESENT").allowed)
        assertFalse(SupervisorDutyPolicy.evaluate(true, true, null).allowed)
        assertFalse(SupervisorDutyPolicy.evaluate(true, false, "PRESENT").allowed)
    }
}
