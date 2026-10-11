package com.manger.app.data.local

import com.manger.app.domain.*

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

object ArchiveSnapshot {
    fun encode(data: MonthData): String {
        val root = JSONObject().put("month", data.month.toString())
        val staff = JSONArray()
        for (e in data.employees) {
            staff.put(JSONObject().put("id", e.id).put("code", e.code)
                .put("name", e.name).put("site", e.site).put("roster_number", e.rosterNumber)
                .put("started", e.started.toString())
                .put("ended", e.ended?.toString() ?: JSONObject.NULL)
                .put("active", e.active))
        }
        val marks = JSONArray()
        for ((key, status) in data.marks) {
            marks.put(JSONObject().put("employee_id", key.first)
                .put("day", key.second.toString()).put("status", status.name))
        }
        val extras = JSONArray()
        for((key,value) in data.columnValues) extras.put(JSONObject().put("employee_id",key.first)
            .put("column_key",key.second).put("value",value))
        return root.put("employees", staff).put("marks", marks).put("extra_columns",extras).toString()
    }

    fun decode(json: String): MonthData {
        val root = JSONObject(json)
        val month = YearMonth.parse(root.getString("month"))
        val es = root.getJSONArray("employees")
        val employees = buildList {
            for (i in 0 until es.length()) {
                val e = es.getJSONObject(i)
                add(Employee(e.getLong("id"), e.getString("code"), e.getString("name"),
                    LocalDate.parse(e.getString("started")),
                    if (e.isNull("ended")) null else LocalDate.parse(e.getString("ended")),
                    e.getBoolean("active"), e.optString("site", ""), e.optString("roster_number", "")))
            }
        }
        val stored = root.getJSONArray("marks")
        val marks = buildMap {
            for (i in 0 until stored.length()) {
                val x = stored.getJSONObject(i)
                val day = LocalDate.parse(x.getString("day"))
                require(YearMonth.from(day) == month) { "حالة بتاريخ خارج شهر الأرشيف" }
                
                val status = Attendance.fromStored(x.getString("status"))
                put(x.getLong("employee_id") to day, status)
            }
        }
        val extras = root.optJSONArray("extra_columns") ?: JSONArray()
        val values = buildMap {
            for(i in 0 until extras.length()) {
                val x=extras.getJSONObject(i)
                put(x.getLong("employee_id") to x.getString("column_key"),x.getString("value"))
            }
        }
        return MonthData(month, EmployeeOrder.employees(employees), marks,values)
    }
}
