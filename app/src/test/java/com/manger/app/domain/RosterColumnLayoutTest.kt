package com.manger.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RosterColumnLayoutTest {
    @Test fun renameRetainsStableIdentity() {
        val rank = RosterColumn("custom_123456789abc", "الرتبة")
        val columns = listOf(RosterColumn("name", "اسم القوة"), rank, RosterColumn("roster", "الرقم (ع)"))
        assertEquals(columns, RosterColumnLayout.decode(RosterColumnLayout.encode(columns)))
        val employee = Employee(77L,"0077","سالم محمد",LocalDate.of(2026,1,1),null,true,"سيئون","A77")
        assertEquals("ملازم", RosterColumnLayout.value(rank,employee,1,mapOf((77L to rank.key) to "ملازم")))
        val renamed = rank.copy(title = "الرتبة العسكرية")
        assertEquals("ملازم", RosterColumnLayout.value(renamed,employee,1,mapOf((77L to rank.key) to "ملازم")))
    }
    @Test fun mandatoryNameAndUniqueTitles() {
        assertTrue(runCatching { RosterColumnLayout.validate(listOf(RosterColumn("name","اسم",false))) }.isFailure)
        assertTrue(runCatching { RosterColumnLayout.validate(listOf(RosterColumn("name","اسم"),RosterColumn("site","اسم"))) }.isFailure)
    }
    @Test fun oldLayoutsRemainReadable() {
        assertEquals(RosterColumnLayout.defaults,RosterColumnLayout.decode("ordinal,roster,name,site"))
    }
}
