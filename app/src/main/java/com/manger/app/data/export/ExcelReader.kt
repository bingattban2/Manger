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

  
object ExcelReader {
     
 
    fun readNames(source: InputStream, name: String): List<String> = readEmployees(source, name).map { it.name }

    fun readEmployees(source: InputStream, name: String): List<ImportedEmployee> =
        readEmployeeSheets(source, name).values.flatten()

      
    fun readEmployeeSheets(source: InputStream, name: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }): LinkedHashMap<String, List<ImportedEmployee>> {
        val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        if (extension == "xls") error("هذه صيغة Excel قديمة (.xls). احفظ الملف بصيغة Excel Workbook (.xlsx) ثم أعد الاستيراد")
        if (extension == "csv") {
            val bytes = source.readBytes()
            require(bytes.size <= 25_000_000) { "ملف CSV كبير جداً" }
            val text = when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                    String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                    String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                else -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            }
            val rows = extractRecords(parseCsvRows(text), "CSV")
            require(rows.isNotEmpty()) { "لم يتم العثور على أسماء صالحة في CSV" }
            onProgress(1,1)
            return linkedMapOf("CSV" to rows)
        }
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(source).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val sheet = entry.name.startsWith("xl/worksheets/") &&
                        entry.name.endsWith(".xml") && entry.name.removePrefix("xl/worksheets/").indexOf('/') < 0
                    if (!entry.isDirectory && (sheet || entry.name in setOf("xl/sharedStrings.xml", "xl/workbook.xml", "xl/_rels/workbook.xml.rels"))) {
                        val buffer = ByteArray(8192)
                        val bytes = ByteArrayOutputStream()
                        while (true) {
                            val read = zip.read(buffer)
                            if (read == -1) break
                            total += read
                            require(total <= 30_000_000) { "حجم بيانات Excel كبير جداً (الحد 30 ميجابايت)" }
                            bytes.write(buffer, 0, read)
                        }
                        parts[entry.name] = bytes.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: IllegalArgumentException) { throw e }
          catch (e: Exception) { throw IllegalArgumentException("تعذرت قراءة الملف. تأكد أنه XLSX سليم وغير محمي بكلمة مرور", e) }
        val sheets = parts.filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
        require(sheets.isNotEmpty()) { "لم يتم العثور على أوراق Excel. اختر ملف XLSX صحيحاً، وليس XLS أو XLSB" }
        val shared = parts["xl/sharedStrings.xml"]?.let { bytes ->
            elements(parseXml(bytes), "si").map { si ->
                elements(si, "t").joinToString("") { it.textContent.orEmpty() }
            }
        } ?: emptyList()
        
        val humanNames = runCatching {
            val relationships = parts["xl/_rels/workbook.xml.rels"]?.let(::parseXml)
            val workbook = parts["xl/workbook.xml"]?.let(::parseXml)
            if (relationships == null || workbook == null) emptyMap() else {
                val targets = elements(relationships,"Relationship").associate { rel ->
                    val target = rel.getAttribute("Target").replace('\\','/')
                    rel.getAttribute("Id") to ("xl/worksheets/" + target.substringAfterLast("worksheets/"))
                }
                elements(workbook,"sheet").mapNotNull { el ->
                    val rid = el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id")
                        .ifBlank { el.getAttribute("r:id") }
                    targets[rid]?.let { it to el.getAttribute("name") }
                }.toMap()
            }
        }.getOrDefault(emptyMap())
        val parsed = linkedMapOf<String, List<ImportedEmployee>>()
        sheets.entries.sortedWith(compareBy { it.key.substringAfterLast("/sheet").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }).forEachIndexed { sheetIndex, (path, data) ->
            val sheetName = humanNames[path]?.ifBlank { null }
                ?: path.substringAfterLast('/').removeSuffix(".xml")
            val rows = elements(parseXml(data), "row").map { row ->
                val cells = linkedMapOf<Int, String>()
                var sequentialIndex = 0
                for (cell in elements(row, "c")) {
                    val ref = cell.getAttribute("r").takeWhile(Char::isLetter)
                    val index = if (ref.isEmpty()) sequentialIndex else ref.fold(0) { acc, ch ->
                        acc * 26 + (ch.uppercaseChar() - 'A' + 1)
                    } - 1
                    sequentialIndex = index + 1
                    val raw = if (cell.getAttribute("t") == "inlineStr")
                        elements(cell, "t").joinToString("") { it.textContent.orEmpty() }
                    else elements(cell, "v").firstOrNull()?.textContent.orEmpty()
                    val value = if (cell.getAttribute("t") == "s")
                        shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                    if (index >= 0 && value.isNotBlank()) cells[index] = value.trim()
                }
                cells
            }.filter(Map<Int, String>::isNotEmpty)
            val records = extractRecords(rows, sheetName)
            if (records.isNotEmpty()) {
                require(sheetName !in parsed) { "يوجد اسم ورقة مكرر: $sheetName" }
                parsed[sheetName] = records
            }
            onProgress(sheetIndex + 1, sheets.size)
        }
        require(parsed.isNotEmpty()) { "لا توجد أوراق تحتوي كشفاً صالحاً مع أعمدة الرقم (ع) والاسم والموقع" }
        return parsed
    }

    private fun normalized(s: String) = s.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace(Regex("[إأآٱ]"), "ا")
        .replace(Regex("\\s+"), " ")

    private fun isNameHeader(s: String): Boolean {
        val value = normalized(s)
        if (value.contains("كشف ") || value.contains("قائمة ") || value.contains("تقرير ")) return false
        return value == "الاسم" || value == "اسم" || value.startsWith("اسم الفرد") || value.startsWith("اسم الموظف") ||
            value.startsWith("اسم العامل") || value.startsWith("الاسم ") ||
            value.startsWith("اسم كامل") || value == "name" || value == "full name" ||
            value == "employee name" || value == "employee" || value == "staff name"
    }

    private fun isLikelyName(value: String): Boolean {
        val text = value.trim()
        if (text.length < 2 || isNameHeader(text) || text.length > 120) return false
        if (!text.any(Char::isLetter) || text.matches(Regex("[0-9٠-٩۰-۹ .+\\-/]+"))) return false
        if (normalized(text).contains("كشف الافراد") || normalized(text).contains("ادارة الافراد") ||
            normalized(text).contains("كشف الموظفين") || normalized(text).contains("ادارة الموظفين") ||
            normalized(text).startsWith("اجمالي ") || normalized(text).startsWith("تاريخ ")) return false
        return true
    }

    private fun isSiteHeader(value: String): Boolean {
        val v=normalized(value)
        return v == "الموقع" || v == "موقع" || v == "موقع العمل" ||
            v == "المقر" || v == "الفرع" || v == "مكان العمل" ||
            v == "site" || v == "location" || v == "work location" || v == "branch"
    }

    private fun isRosterHeader(value: String): Boolean {
        val v = normalized(value).replace(Regex("[()\\s_ـ]+"), "")
        return v in setOf("الرقمع", "رقمع", "الرقم(ع)", "رقم(ع)", "الرقمالعام", "رقمالعام", "rosternumber")
    }

    private fun extractRecords(rows: List<Map<Int, String>>, sheet: String): List<ImportedEmployee> {
        if (rows.isEmpty()) return emptyList()
        val header = rows.take(40).withIndex().firstNotNullOfOrNull { (rowIndex, row) ->
            row.entries.firstOrNull { isNameHeader(it.value) }?.key?.let { rowIndex to it }
        } ?: return emptyList() 
        val columns = rows[header.first]
        val siteCol = columns.entries.firstOrNull { isSiteHeader(it.value) }?.key
        val rosterCol = columns.entries.firstOrNull { isRosterHeader(it.value) }?.key
        require(siteCol != null && rosterCol != null) {
            "الورقة $sheet: يجب توفر الأعمدة الثلاثة: الرقم (ع)، الاسم، الموقع"
        }
        val records = mutableListOf<ImportedEmployee>()
        rows.drop(header.first + 1).forEachIndexed { index, row ->
            val name = row[header.second].orEmpty().trim()
            val number = row[rosterCol].orEmpty().trim()
            val site = row[siteCol].orEmpty().trim()
            if (name.isBlank() && number.isBlank() && site.isBlank()) return@forEachIndexed
            
            if (name.isNotBlank() && !isLikelyName(name) && number.isBlank()) return@forEachIndexed
            require(isLikelyName(name) && number.isNotBlank() && site.isNotBlank()) {
                "بيانات ناقصة في الورقة $sheet، السطر ${header.first + index + 2}: الرقم (ع)، الاسم والموقع مطلوبة"
            }
            records.add(ImportedEmployee(name, site, RosterNumbers.clean(number), sheet))
        }
        return records
    }

    private fun parseCsvRows(text: String): List<Map<Int, String>> {
        val firstLines = text.lineSequence().take(6).toList()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { delimiter ->
            firstLines.sumOf { line -> line.count { it == delimiter } }
        } ?: ','
        val records = mutableListOf<List<String>>()
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { cells.add(current.toString().trim()); current.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    cells.add(current.toString().trim()); current.setLength(0)
                    if (cells.any(String::isNotBlank)) records.add(cells.toList())
                    cells.clear()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString().trim())
        if (cells.any(String::isNotBlank)) records.add(cells.toList())
        return records.map { cellsInRow -> cellsInRow.withIndex()
            .filter { it.value.isNotBlank() }.associate { it.index to it.value } }
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        
        
        val prefix=if(bytes.size>=2 && bytes[0]==0xFF.toByte() && bytes[1]==0xFE.toByte())
            String(bytes,Charsets.UTF_16LE)
        else if(bytes.size>=2 && bytes[0]==0xFE.toByte() && bytes[1]==0xFF.toByte())
            String(bytes,Charsets.UTF_16BE)
        else bytes.toString(Charsets.UTF_8)
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)\\b",RegexOption.IGNORE_CASE).containsMatchIn(prefix)) {
            "لا يُسمح بتعريف كيانات أو DTD داخل ملف Excel لأسباب أمنية"
        }
        val factory=DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware=true
        runCatching { factory.isExpandEntityReferences=false }
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities",false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false) }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        val parser=factory.newDocumentBuilder()
        parser.setEntityResolver { _,_ -> InputSource(StringReader("")) }
        return parser.parse(ByteArrayInputStream(bytes))
    }
    private fun elements(node: org.w3c.dom.Node, local: String): List<Element> {
        val list = if(node is org.w3c.dom.Document) node.getElementsByTagNameNS("*",local) else (node as Element).getElementsByTagNameNS("*",local)
        return (0 until list.length).mapNotNull { list.item(it) as? Element }
    }
}
