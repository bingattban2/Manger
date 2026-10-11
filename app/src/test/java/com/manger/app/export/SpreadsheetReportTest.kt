package com.manger.app.data.export

import com.manger.app.domain.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream

class SpreadsheetReportTest {
    @Test fun sortedExportHasSequentialNumbersAndImportsNamesAgain() {
        val d = LocalDate.of(2026, 10, 1)
        val people = EmployeeOrder.employees(listOf(
            Employee(1,"077","يوسف",d,null,true),
            Employee(2,"013","أحمد",d,null,true)
        ))
        val output=ByteArrayOutputStream()
        Spreadsheet.write(MonthData(YearMonth.of(2026,10),people,emptyMap()),output)
        val bytes=output.toByteArray()
        assertEquals(listOf("أحمد", "يوسف"),Spreadsheet.readNames(ByteArrayInputStream(bytes),"test.xlsx"))
        var sheet = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while(true) {
                val entry = zip.nextEntry ?: break
                if(entry.name=="xl/worksheets/sheet1.xml") sheet=zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertTrue(sheet.contains("rightToLeft=\"1\""))
        assertTrue(sheet.contains("<c r=\"A2\" t=\"inlineStr\" s=\"0\"><is><t>001</t></is></c>"))
        assertTrue(sheet.contains("<c r=\"A3\" t=\"inlineStr\" s=\"0\"><is><t>002</t></is></c>"))
    }
}
