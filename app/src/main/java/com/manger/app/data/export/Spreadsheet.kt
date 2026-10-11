package com.manger.app.data.export
import com.manger.app.domain.*
import java.io.InputStream
import java.io.OutputStream

object Spreadsheet {
    fun write(month: MonthData, dest: OutputStream, rosterColumns: List<RosterColumn> = RosterColumnLayout.defaults) =
        ExcelWriter.write(month, dest, rosterColumns)
    fun writePeriodReport(report: PeriodReport, dest: OutputStream) = ExcelWriter.writePeriodReport(report, dest)
    fun readNames(source: InputStream, name: String): List<String> = ExcelReader.readNames(source, name)
    fun readEmployees(source: InputStream, name: String): List<ImportedEmployee> = ExcelReader.readEmployees(source, name)
    fun readEmployeeSheets(source: InputStream, name: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): LinkedHashMap<String,List<ImportedEmployee>> =
        ExcelReader.readEmployeeSheets(source, name, onProgress)
}
