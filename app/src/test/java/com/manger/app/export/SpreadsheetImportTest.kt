package com.manger.app.data.export

import com.manger.app.domain.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpreadsheetImportTest {
    @Test fun findsNamesOnOtherSheetAfterTitleWithSharedStrings() {
        val shared = """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>اسم الفرد</t></si><si><t>أحمد سعيد</t></si><si><t>سالم محمد</t></si></sst>"""
        val otherSheet = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>كشف 2026</t></is></c></row><row r="3"><c r="A3"><v>1</v></c><c r="B3" t="s"><v>0</v></c></row><row r="4"><c r="A4"><v>2</v></c><c r="B4" t="s"><v>1</v></c></row><row r="5"><c r="A5"><v>3</v></c><c r="B5" t="s"><v>2</v></c></row></sheetData></worksheet>"""
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            mapOf(
                "xl/sharedStrings.xml" to shared,
                "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>""",
                "xl/worksheets/sheet7.xml" to otherSheet
            ).forEach { (path, xml) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        assertEquals(listOf("أحمد سعيد", "سالم محمد"), Spreadsheet.readNames(ByteArrayInputStream(output.toByteArray()), "staff.xlsx"))
    }

    @Test fun readsCsvSeparatedWithSemicolonAndTitleRow() {
        val csv = "كشف الافراد\nرقم;اسم الفرد\n1;عبدالله صالح\n2;محمد أحمد\n"
        assertEquals(listOf("عبدالله صالح", "محمد أحمد"), Spreadsheet.readNames(ByteArrayInputStream(csv.toByteArray()), "staff.csv"))
    }

    @Test fun tellsUserWhenLegacyXlsIsUnsupported() {
        val error = runCatching {
            Spreadsheet.readNames(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "old.xls")
        }.exceptionOrNull()?.message.orEmpty()
        assertTrue(error.contains(".xlsx"))
    }
}
