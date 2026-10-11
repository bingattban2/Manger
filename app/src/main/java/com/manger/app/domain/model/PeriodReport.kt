package com.manger.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

  
data class PeriodReportDay(val date: LocalDate, val status: Attendance)
data class PeriodReportEmployee(val employee: Employee, val days: List<PeriodReportDay>) {
    val counts: Map<String, Int> by lazy(LazyThreadSafetyMode.NONE) { days.groupingBy { it.status.name }.eachCount() }
    val byMonth: Map<YearMonth, List<PeriodReportDay>> by lazy(LazyThreadSafetyMode.NONE) { days.groupBy { YearMonth.from(it.date) } }
}
data class PeriodReport(
    val start: LocalDate, val end: LocalDate,
    val rows: List<PeriodReportEmployee>, val unavailableMonths: List<YearMonth>
) {
    val totalDays: Int by lazy(LazyThreadSafetyMode.NONE) { rows.sumOf { it.days.size } }
}

object PeriodReportBuilder {
      
    fun create(employees: List<Employee>, from: LocalDate, to: LocalDate, today: LocalDate,
               monthData: (YearMonth) -> MonthData?): PeriodReport {
        require(employees.isNotEmpty()) { "اختر اسماً واحداً على الأقل" }
        require(!from.isAfter(to)) { "تاريخ البداية يجب ألا يتجاوز تاريخ النهاية" }
        require(!to.isAfter(today)) { "لا يمكن إنشاء تقرير عن أيام مستقبلية" }
        require(ChronoUnit.DAYS.between(from, to) <= 1826) { "أقصى مدة للتقرير خمس سنوات" }
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(to)) }.toList()
        val available = months.associateWith { monthData(it) }
        val missing = available.filterValues { it == null }.keys.toList()
        val archiveRoster = available.mapValues { (_, data) -> data?.employees?.associateBy { it.id }.orEmpty() }
        val rows = employees.map { employee ->
            val days = available.filterValues { it != null }.flatMap { (month, dataValue) ->
                val data = dataValue!!
                val snapshotEmployee = archiveRoster[month]?.get(employee.id)
                if (snapshotEmployee == null) emptyList() else {
                    val lower = maxOf(from, data.month.atDay(1))
                    val upper = minOf(to, data.month.atEndOfMonth())
                    generateSequence(lower) { it.plusDays(1) }.takeWhile { !it.isAfter(upper) }
                        .mapNotNull { day -> data.status(snapshotEmployee, day, today)?.let { PeriodReportDay(day, it) } }.toList()
                }
            }
            PeriodReportEmployee(employee, days)
        }
        return PeriodReport(from, to, rows, missing)
    }
}
