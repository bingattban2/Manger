package com.manger.app.data.export

import com.manger.app.domain.*

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import org.xml.sax.InputSource
import javax.xml.XMLConstants
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

  
object ExcelWriter {
    private fun xmlEscape(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun column(n: Int): String { var i=n; var r=""; while(i>0) { i--; r=('A'+i%26).toString()+r; i/=26 }; return r }
    private fun ZipOutputStream.entry(path: String, content: String) { putNextEntry(ZipEntry(path)); write(content.toByteArray(Charsets.UTF_8)); closeEntry() }

    private fun lightRgb(argb: Long): String {
        fun channel(shift: Int): Int {
            val base = ((argb shr shift) and 255L).toInt()
            return (base * .23 + 255 * .77).toInt().coerceIn(0, 255)
        }
        return "FF%02X%02X%02X".format(java.util.Locale.ROOT,
            channel(16), channel(8), channel(0))
    }

    private fun styles(): String {
        val states = Attendance.entries
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts>""")
            append("<fills count=\"${2 + states.size}\">")
            append("""<fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""")
            states.forEach { st -> append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"${lightRgb(st.argb)}\"/><bgColor indexed=\"64\"/></patternFill></fill>") }
            append("</fills>")
            append("""<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
            append("<cellXfs count=\"${2 + states.size}\">")
            append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""")
            states.indices.forEach { i -> append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"${i + 2}\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>") }
            append("</cellXfs></styleSheet>")
        }
    }

    fun write(month: MonthData, dest: OutputStream,
              rosterColumns: List<RosterColumn> = RosterColumnLayout.defaults) {
        val columns = RosterColumnLayout.sanitize(rosterColumns).filter { it.visible }
        val frozen = columns.size
        val today = LocalDate.now()
        ZipOutputStream(dest).use { zip ->
            zip.entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            zip.entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Attendance" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            zip.entry("xl/styles.xml", styles())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val output = java.io.OutputStreamWriter(zip, Charsets.UTF_8)
            fun append(text: String) { output.write(text) }
            run {
                append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"><pane xSplit="$frozen" ySplit="1" topLeftCell="${column(frozen+1)}2" activePane="bottomRight" state="frozen"/></sheetView></sheetViews><cols>""")
                columns.forEachIndexed { idx, c ->
                    val width = when(c.key) { "name" -> 34; "site" -> 23; "roster" -> 15; "ordinal" -> 12; else -> 20 }
                    append("<col min=\"${idx+1}\" max=\"${idx+1}\" width=\"$width\" customWidth=\"1\"/>")
                }
                append("<col min=\"${frozen+1}\" max=\"${frozen+month.month.lengthOfMonth()}\" width=\"5\" customWidth=\"1\"/></cols><sheetData>")
                fun cell(c: Int, row: Int, value: String, style: Int) { append("<c r=\"${column(c)}$row\" t=\"inlineStr\" s=\"$style\"><is><t>${xmlEscape(value)}</t></is></c>") }
                val legendStart = month.month.lengthOfMonth() + frozen + 1
                append("<row r=\"1\">")
                columns.forEachIndexed { idx,c -> cell(idx+1,1,c.title,1) }
                for (day in 1..month.month.lengthOfMonth()) cell(day+frozen,1,day.toString(),1)
                cell(legendStart,1,"رمز الحالة",1); cell(legendStart+1,1,"اسم الحالة",1)
                append("</row>")
                val states = Attendance.entries
                val count = maxOf(month.employees.size, states.size)
                for (index in 0 until count) {
                    val row = index + 2
                    append("<row r=\"$row\">")
                    val employee = month.employees.getOrNull(index)
                    if (employee != null) {
                        columns.forEachIndexed { idx,c ->
                            cell(idx+1,row,RosterColumnLayout.value(c,employee,index+1,month.columnValues),0)
                        }
                        for (day in 1..month.month.lengthOfMonth()) {
                            val date=month.month.atDay(day)
                            val state=month.status(employee,date,today)
                            if(state!=null) cell(day+frozen,row,state.symbol,
                                states.indexOfFirst { it.name == state.name }.coerceAtLeast(0) + 2)
                        }
                    }
                    states.getOrNull(index)?.let { state ->
                        cell(legendStart,row,state.symbol,index+2)
                        cell(legendStart+1,row,state.label,0)
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            output.flush()
            zip.closeEntry()
        }
    }

      
    fun writePeriodReport(report: PeriodReport, dest: OutputStream) {
        val states = Attendance.entries
        fun sheet(header: List<String>, entries: List<Pair<List<String>, Int?>>,
                  introductoryRows: List<List<String>> = emptyList(),
                  numericColumns: Set<Int> = emptySet()): String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetData>""")
            var index = 0
            fun row(values: List<String>, headerStyle: Boolean = false, statusStyle: Int? = null) {
                index++
                append("<row r=\"$index\">")
                values.forEachIndexed { col, item ->
                    val style = if (headerStyle) 1 else if (statusStyle != null && col == values.lastIndex) statusStyle else 0
                    val cellRef = "${column(col + 1)}$index"
                    if (!headerStyle && col in numericColumns && item.toIntOrNull() != null)
                        append("<c r=\"$cellRef\" s=\"$style\"><v>$item</v></c>")
                    else append("<c r=\"$cellRef\" t=\"inlineStr\" s=\"$style\"><is><t>${xmlEscape(item)}</t></is></c>")
                }
                append("</row>")
            }
            row(header, headerStyle = true)
            introductoryRows.forEach { row(it) }
            entries.forEach { (values, style) -> row(values, statusStyle = style) }
            append("</sheetData></worksheet>")
        }
        fun label(s: Attendance) = s.label
        val header = listOf("الرقم (ع)", "اسم الفرد", "الموقع", "من", "إلى", "إجمالي الأيام") + states.map(::label)
        val missing = report.unavailableMonths.joinToString(", ") { it.toString() }
        val intro = if (missing.isNotBlank()) listOf(listOf("تنبيه: الأشهر غير المتوفرة بالأرشيف", missing)) else emptyList()
        val summary = report.rows.map { r ->
            (listOf(r.employee.rosterNumber, r.employee.name, r.employee.site, report.start.toString(), report.end.toString(), r.days.size.toString()) +
                states.map { (r.counts[it.name] ?: 0).toString() }) to null
        }
        val monthly = report.rows.flatMap { r ->
            r.byMonth.toSortedMap().map { (month, days) ->
                (listOf(r.employee.rosterNumber, r.employee.name, r.employee.site, month.toString(), days.size.toString()) +
                    states.map { state -> days.count { it.status.name == state.name }.toString() }) to null
            }
        }
        val weekday = java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.forLanguageTag("ar"))
        val daily = report.rows.flatMap { r ->
            r.days.sortedBy { it.date }.map { d ->
                val style = states.indexOfFirst { it.name == d.status.name }.takeIf { it >= 0 }?.plus(2)
                listOf(r.employee.rosterNumber, r.employee.name, r.employee.site,
                    d.date.toString(), d.date.format(weekday), YearMonth.from(d.date).toString(), d.status.label) to style
            }
        }
        val contents = listOf(
            sheet(header, summary, intro, (5 until header.size).toSet()),
            sheet(listOf("الرقم (ع)", "اسم الفرد", "الموقع", "الشهر", "الأيام") + states.map(::label), monthly,
                numericColumns = (4 until 5 + states.size).toSet()),
            sheet(listOf("الرقم (ع)", "اسم الفرد", "الموقع", "التاريخ", "اليوم", "الشهر", "الحالة"), daily)
        )
        ZipOutputStream(dest).use { zip ->
            zip.entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${(1..3).joinToString("") { "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
            zip.entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="الملخص" sheetId="1" r:id="rId1"/><sheet name="حسب الشهر" sheetId="2" r:id="rId2"/><sheet name="حسب اليوم" sheetId="3" r:id="rId3"/></sheets></workbook>""")
            zip.entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${(1..3).joinToString("") { "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>" }}<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            zip.entry("xl/styles.xml", styles())
            contents.forEachIndexed { index, xml -> zip.entry("xl/worksheets/sheet${index + 1}.xml", xml) }
        }
    }


}
