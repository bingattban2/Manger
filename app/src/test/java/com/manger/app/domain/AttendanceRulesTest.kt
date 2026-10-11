package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AttendanceRulesTest {
    private val day=LocalDate.of(2026,10,11)
    @Test fun defaultIsPresentOnlyOnCurrentAndPastDays() {
        assertEquals(Attendance.PRESENT, AttendanceRules.defaultFor(day,day,true,null))
        assertNull(AttendanceRules.defaultFor(day.plusDays(1),day,true,null))
        assertNull(AttendanceRules.defaultFor(day,day,false,null))
        assertEquals(Attendance.LEAVE, AttendanceRules.defaultFor(day.plusDays(1),day,true,Attendance.LEAVE))
    }
    @Test fun countsDefaultPresenceWithoutRows() {
        val e1=Employee(1,"001","أحمد",day.minusMonths(1),null,true)
        val e2=Employee(2,"002","محمد",day.minusMonths(1),null,true)
        val e3=Employee(3,"003","سالم",day.minusMonths(1),null,false)
        val stats=AttendanceRules.count(listOf(e1,e2,e3),mapOf((2L to day) to Attendance.ABSENT),day)
        assertEquals(2,stats.total)
        assertEquals(1,stats.present)
        assertEquals(1,stats.absent)
    }
}
