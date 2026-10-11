package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatusCustomizationTest {
    @Test fun changedLabelAndCustomStatusUseStableCodesAndCounts() {
        val changed = Attendance("ABSENT", "ع", "غياب معدل", 0xFF123456L)
        val training = Attendance("CUSTOM_TRAINING", "ت", "تدريب", 0xFF8E44ADL)
        Attendance.configure(listOf(Attendance.PRESENT, changed, Attendance.LEAVE, Attendance.PERMISSION, training))
        try {
            assertEquals("غياب معدل", Attendance.fromStored("ABSENT").label)
            assertEquals("تدريب", Attendance.fromStored("CUSTOM_TRAINING").label)
            val date = LocalDate.of(2026, 10, 11)
            val employee = Employee(1, "001", "محمد", date.minusDays(2), null, true, "المكلا")
            val stats = AttendanceRules.count(listOf(employee), mapOf((1L to date) to training), date)
            assertEquals(1, stats.total)
            assertEquals(1, stats.count(training))
            assertEquals(0, stats.present)
        } finally {
            Attendance.configure(Attendance.defaults)
        }
    }
}
