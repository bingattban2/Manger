package com.manger.app.data.export

import com.manger.app.domain.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpreadsheetSitesTest {
    @Test fun importsNameAndSiteFromCsv() {
        val csv = "رقم;اسم الفرد;الموقع\n1;أحمد سالم;المكلا\n2;محمد علي;سيئون\n"
        val result=Spreadsheet.readEmployees(ByteArrayInputStream(csv.toByteArray()),"staff.csv")
        assertEquals(listOf(ImportedEmployee("أحمد سالم","المكلا"),ImportedEmployee("محمد علي","سيئون")),result)
    }

    @Test fun importsAndExportsSiteColumnInXlsx() {
        val employee=Employee(1,"001","أحمد سالم",LocalDate.of(2026,1,1),null,true,"المكلا")
        val stream=ByteArrayOutputStream()
        Spreadsheet.write(MonthData(YearMonth.of(2026,10),listOf(employee),emptyMap()),stream)
        assertEquals(listOf(ImportedEmployee("أحمد سالم","المكلا")),
            Spreadsheet.readEmployees(ByteArrayInputStream(stream.toByteArray()),"report.xlsx"))
    }

    @Test fun rejectsUnsafeDoctype() {
        val xml="""<!DOCTYPE worksheet [<!ENTITY hack SYSTEM "file:///etc/passwd">]><worksheet/>"""
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }
        val failure=runCatching {
            Spreadsheet.readEmployees(ByteArrayInputStream(out.toByteArray()),"data.xlsx")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("DTD"))
    }
}
