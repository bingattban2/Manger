package com.manger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EmployeeOrderTest {
    @Test fun arabicNamesAreAlphabeticalRegardlessOfSavedIds() {
        val d = LocalDate.of(2026, 10, 11)
        val input = listOf(
            Employee(1, "001", "محمد حسن", d, null, true),
            Employee(2, "002", "أحمد علي", d, null, true),
            Employee(3, "003", "سالم عمر", d, null, true)
        )
        assertEquals(listOf("أحمد علي", "سالم عمر", "محمد حسن"), EmployeeOrder.employees(input).map { it.name })
        // Sorting must not rewrite the persisted employee codes or IDs.
        assertEquals(listOf("002", "003", "001"), EmployeeOrder.employees(input).map { it.code })
    }

    @Test fun importedNamesAreCleanedSortedAndDeduplicated() {
        assertEquals(listOf("آدم سالم", "خالد علي", "يوسف حسن"), EmployeeOrder.newNames(
            listOf(" يوسف حسن ", "آدم سالم", "خالد   علي", "أدم سالم", "أحمد محمد", "خالد علي"),
            listOf("أَحمد محمد")
        ))
    }
}
