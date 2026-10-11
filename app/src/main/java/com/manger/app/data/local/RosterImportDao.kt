package com.manger.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.manger.app.domain.Employee
import com.manger.app.domain.ImportCheck
import com.manger.app.domain.ImportSummary
import com.manger.app.domain.ImportedEmployee
import com.manger.app.domain.EmployeeOrder
import com.manger.app.domain.RosterNumbers
import org.json.JSONObject
import java.time.LocalDate

  
internal object RosterImportDao {
    fun importRoster(
        db: SQLiteDatabase, records: List<ImportedEmployee>, existingEmployees: List<Employee>,
        preview: ImportCheck, onProgress: (Int, Int) -> Unit,
        recordMutation: (SQLiteDatabase, String, String, JSONObject) -> Unit
    ): ImportSummary {
        if (preview.added + preview.updated == 0) return ImportSummary(0, 0, 0)
        var added = 0
        var updated = 0
        var sitesFilled = 0
        val existingByNumber = existingEmployees.filter { it.rosterNumber.isNotBlank() }.associateBy { it.rosterNumber }
        var next = (existingEmployees.mapNotNull { it.code.toLongOrNull() }.maxOrNull() ?: 0L) + 1
        db.beginTransaction()
        try {
            for ((index, row) in records.withIndex()) {
                val number = RosterNumbers.clean(row.rosterNumber)
                val cleanName = EmployeeOrder.clean(row.name)
                val site = row.site.trim().take(120)
                val existing = existingByNumber[number]
                if (existing == null) {
                    val values = ContentValues().apply {
                        put("code", (next++).toString().padStart(3, '0'))
                        put("roster_number", number)
                        put("name", cleanName)
                        put("site", site)
                        put("started", LocalDate.now().toString())
                        put("active", 1)
                    }
                    db.insertOrThrow("employees", null, values)
                    added++
                } else if (existing.name != cleanName || existing.site != site) {
                    if (existing.site != site) {
                        val linked = db.rawQuery(
                            "SELECT 1 FROM local_users WHERE employee_id=? LIMIT 1", arrayOf(existing.id.toString())
                        ).use { it.moveToFirst() }
                        require(!linked) {
                            "لا يمكن نقل فرد مرتبط بحساب مشرف: ${existing.name}. عدل الحساب من إدارة المشرفين أولاً."
                        }
                    }
                    val values = ContentValues().apply { put("name", cleanName); put("site", site) }
                    db.update("employees", values, "id=?", arrayOf(existing.id.toString()))
                    if (existing.site.isBlank() && site.isNotBlank()) sitesFilled++
                    updated++
                }
                if (existing == null || existing.name != cleanName || existing.site != site) {
                    recordMutation(db, number, site, JSONObject()
                        .put("roster_number", number).put("name", cleanName).put("site", site))
                }
                if (index % 50 == 49 || index == records.lastIndex) onProgress(index + 1, records.size)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportSummary(added, sitesFilled, updated)
    }
}
