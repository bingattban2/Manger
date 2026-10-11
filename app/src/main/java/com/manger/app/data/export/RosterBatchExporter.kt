package com.manger.app.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.manger.app.domain.Employee
import com.manger.app.domain.MonthData
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

  
object RosterBatchExporter {
    const val APP_FOLDER = "إدارة التمام اليومي"
    enum class Format(val extension: String, val mime: String) {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("pdf", "application/pdf")
    }
    data class Choice(val fullRoster: Boolean, val sites: Set<String>)
    data class Outcome(val files: Int, val folderLabel: String)

    private fun safeFilePart(value: String) = value.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
        .take(70).ifEmpty { "غير_محدد" }

      
    fun locationLabel(context: Context, selectedTree: Uri?): String = when {
        selectedTree != null -> "المجلد الذي اخترته في مدير الملفات"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "التنزيلات / $APP_FOLDER"
        else -> (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "مجلد التطبيق") + "/$APP_FOLDER"
    }

    private fun writeFile(context: Context, tree: Uri?, fileName: String, mime: String,
                          writer: (OutputStream) -> Unit) {
        val resolver = context.contentResolver
        if (tree != null) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val target = DocumentsContract.createDocument(resolver, parent, mime, fileName)
                ?: error("لم يسمح المجلد المحدد بإنشاء الملف: $fileName")
            try {
                resolver.openOutputStream(target, "w")?.use(writer)
                    ?: error("تعذر فتح ملف التصدير: $fileName")
            } catch (ex: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, target) }
                throw ex
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء الملف داخل التنزيلات")
            try {
                resolver.openOutputStream(uri)?.use(writer) ?: error("تعذر فتح ملف التصدير")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (ex: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw ex
            }
        } else {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: error("وحدة التخزين الخارجية غير متاحة")
            val folder = File(root, APP_FOLDER)
            check(folder.isDirectory || folder.mkdirs()) { "تعذر إنشاء مجلد التطبيق" }
            val target = File(folder, fileName)
            try { target.outputStream().use(writer) }
            catch (ex: Exception) { target.delete(); throw ex }
        }
    }

      
    fun export(context: Context, month: MonthData, choice: Choice, format: Format,
               tree: Uri?, columns: List<com.manger.app.domain.RosterColumn> = com.manger.app.domain.RosterColumnLayout.defaults,
               onProgress: (Int, Int, String) -> Unit): Outcome {
        val orderedSites = choice.sites.sorted()
        require(choice.fullRoster || orderedSites.isNotEmpty()) { "اختر كشفاً واحداً على الأقل" }
        val bySite = month.employees.groupBy { it.site.trim() }
        val idsBySite = bySite.mapValues { (_, people) -> people.mapTo(HashSet()) { it.id } }
        val siteForId = month.employees.associate { it.id to it.site.trim() }
        val marksBySite = HashMap<String, MutableMap<Pair<Long,java.time.LocalDate>, com.manger.app.domain.Attendance>>()
        for ((key, status) in month.marks) {
            val site = siteForId[key.first] ?: continue
            marksBySite.getOrPut(site) { HashMap() }[key] = status
        }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val specs = buildList {
            if (choice.fullRoster) add("000_الكشف_الكلي" to month)
            for ((index, site) in orderedSites.withIndex()) add("${(index + 1).toString().padStart(3, '0')}_موقع_${safeFilePart(site)}" to
                MonthData(month.month, bySite[site].orEmpty(), marksBySite[site].orEmpty(),
                    month.columnValues.filterKeys { it.first in idsBySite[site].orEmpty() }))
        }
        specs.forEachIndexed { index, (label, data) ->
            val filename = "${safeFilePart(label)}_${month.month}_${stamp}.${format.extension}"
            onProgress(index, specs.size, filename)
            writeFile(context, tree, filename, format.mime) { stream ->
                when (format) {
                    Format.XLSX -> Spreadsheet.write(data, stream,columns)
                    Format.PDF -> PdfReport.write(data, stream,columns)
                }
            }
            onProgress(index + 1, specs.size, filename)
        }
        return Outcome(specs.size, locationLabel(context, tree))
    }
}
