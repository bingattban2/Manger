package com.manger.app.data.local

import com.manger.app.domain.*
import java.time.LocalDate
import java.time.YearMonth

internal object AttendanceReportQueries {
    fun periodReport(session: LocalSession, ids: Set<Long>, visible: List<Employee>,
                     from: LocalDate,to: LocalDate,today: LocalDate,
                     loadMonth:(YearMonth)->MonthData,loadArchive:(YearMonth)->MonthData?):PeriodReport {
        require(ids.isNotEmpty()) { "اختر اسماً واحداً على الأقل" }
        val allowed=visible.filter { it.id in ids }
        require(allowed.size==ids.size) { "لا تملك صلاحية عرض بعض الافراد المحددين" }
        if(!session.isAdmin) require(allowed.all { it.site.trim()==session.site }) {
            "الموقع المحدد خارج صلاحياتك"
        }
        val nowMonth=YearMonth.from(today)
        return PeriodReportBuilder.create(allowed,from,to,today) { m ->
            if(m==nowMonth) loadMonth(m) else loadArchive(m)
        }
    }
    fun history(employeeId:Long,visible:List<Employee>, archives:List<YearMonth>, today:LocalDate,
                loadMonth:(YearMonth)->MonthData,loadArchive:(YearMonth)->MonthData?):List<EmployeeMonthReport> {
        require(visible.any { it.id == employeeId }) { "لا تملك صلاحية عرض هذا الفرد" }
        val end=YearMonth.from(today)
        val months=archives.filter { it.isBefore(end) }+end
        return months.distinct().sorted().mapNotNull { m ->
            val data=if(m==end) loadMonth(m) else loadArchive(m) ?: return@mapNotNull null
            val employee=data.employees.firstOrNull { it.id==employeeId } ?: return@mapNotNull null
            val statuses=(1..m.lengthOfMonth()).mapNotNull { day ->
                val date=m.atDay(day)
                if(date.isAfter(today)) null else data.status(employee,date,today)
            }
            val totals=statuses.groupingBy { it.name }.eachCount()
            EmployeeMonthReport(m,totals,statuses.size)
        }
    }
}
