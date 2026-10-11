package com.manger.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.manger.app.domain.Employee
import com.manger.app.domain.RosterColumn
import com.manger.app.domain.RosterColumnLayout

internal object RosterColumnDao {
    fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS employee_custom_values (employee_id INTEGER NOT NULL, column_key TEXT NOT NULL, value TEXT NOT NULL DEFAULT '', PRIMARY KEY(employee_id,column_key), FOREIGN KEY(employee_id) REFERENCES employees(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_custom_column_key ON employee_custom_values(column_key)")
    }
    fun loadLayout(db: SQLiteDatabase): List<RosterColumn> {
        val saved = db.rawQuery("SELECT value FROM app_meta WHERE name='roster_layout'",null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        return RosterColumnLayout.decode(saved)
    }
    fun saveLayout(db: SQLiteDatabase, columns: List<RosterColumn>): String {
        val serialized = RosterColumnLayout.encode(RosterColumnLayout.validate(columns))
        db.execSQL("INSERT OR REPLACE INTO app_meta(name,value) VALUES('roster_layout',?)",arrayOf(serialized))
        return serialized
    }
    fun customValues(db: SQLiteDatabase, roster: List<Employee>): Map<Pair<Long,String>,String> {
        if (roster.isEmpty()) return emptyMap()
        val ids = roster.mapTo(HashSet()) { it.id }
        return db.rawQuery("SELECT employee_id,column_key,value FROM employee_custom_values",null).use { c ->
            buildMap { while(c.moveToNext()) if(c.getLong(0) in ids) put(c.getLong(0) to c.getString(1),c.getString(2)) }
        }
    }
    fun saveValue(db: SQLiteDatabase, employeeId: Long, columnKey: String, value: String) {
        if(value.isBlank()) db.delete("employee_custom_values","employee_id=? AND column_key=?",arrayOf(employeeId.toString(),columnKey))
        else db.insertWithOnConflict("employee_custom_values",null,ContentValues().apply {
            put("employee_id",employeeId);put("column_key",columnKey);put("value",value.trim())
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
}
