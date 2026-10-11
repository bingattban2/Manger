package com.manger.app.presentation

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

  
@Composable
internal fun PeriodReportsScreen(
    people: List<Employee>, isAdmin: Boolean,
    loadReport: suspend (Set<Long>, LocalDate, LocalDate) -> PeriodReport,
    onExcel: (PeriodReport) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(0) } 
    var search by remember { mutableStateOf("") }
    var site by remember { mutableStateOf<String?>(null) }
    var siteOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<Long, Boolean>() }
    val summaryHorizontal = rememberScrollState()
    val now = LocalDate.now()
    var from by remember { mutableStateOf(now.withDayOfMonth(1)) }
    var to by remember { mutableStateOf(now) }
    var report by remember { mutableStateOf<PeriodReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val employeeNames = remember(people) { people.map { it.name }.distinct() }
    val sites = remember(people) { people.map { it.site.trim() }.filter { it.isNotBlank() }.distinct().sorted() }
    val roster = remember(people, site, search) {
        ReportEmployeeFilter.select(people, site, search)
    }
    BackHandler(enabled = mode != 0 && !busy) {
        when (mode) {
            2 -> { mode = 1; report = null }
            1 -> mode = 0
        }
    }
    val weekDay = remember { DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("ar")) }
    val dateLabel = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("ar")) }
    fun pick(initial: LocalDate, set: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, year, month, day -> set(LocalDate.of(year, month + 1, day)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("التقارير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (busy) return@TextButton
                when(mode) { 2 -> { mode = 1; report = null }; 1 -> mode = 0; else -> onExit() }
            }) { Text("رجوع") }
        }
        when (mode) {
            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ElevatedCard(onClick = { mode = 1 }, modifier = Modifier.fillMaxWidth(.9f)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("▤", fontSize = 44.sp, color = MaterialTheme.colorScheme.primary)
                        Text("إنشاء تقرير", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        Text(if (isAdmin) "اختر الافراد من جميع المواقع أو موقع محدد" else "اختر الافراد من موقعك",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { mode = 1 }) { Text("إنشاء تقرير") }
                    }
                }
            }
            1 -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    KnownRecordField(search, { search = it }, modifier = Modifier.weight(1f),
                        options = employeeNames, title = "الاسم أو الرقم (ع)")
                    if (isAdmin) Box(Modifier.width(123.dp)) {
                        OutlinedButton(onClick = { siteOpen = true }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("${site?.ifBlank { "غير محدد" } ?: "الكل"} ▾", maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = siteOpen, onDismissRequest = { siteOpen = false }) {
                            DropdownMenuItem(text = { Text("كل المواقع") }, onClick = {
                                site = null; selected.clear(); siteOpen = false
                            })
                            sites.forEach { name ->
                                DropdownMenuItem(text = { Text(name.ifBlank { "غير محدد" }) }, onClick = {
                                    site = name; selected.clear(); siteOpen = false
                                })
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("الأسماء: ${roster.size} • المحددة: ${selected.size}", Modifier.weight(1f),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        val allVisible = roster.all { selected.containsKey(it.id) }
                        roster.forEach { if (allVisible) selected.remove(it.id) else selected[it.id] = true }
                    }, enabled = roster.isNotEmpty()) {
                        Text(if (roster.isNotEmpty() && roster.all { selected.containsKey(it.id) }) "إلغاء تحديد المعروض" else "تحديد المعروض")
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(roster, key = { it.id }) { person ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (selected.containsKey(person.id)) selected.remove(person.id) else selected[person.id] = true
                        }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected.containsKey(person.id), onCheckedChange = { checked ->
                                if (checked) selected[person.id] = true else selected.remove(person.id)
                            })
                            Column(Modifier.weight(1f)) {
                                Text(person.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${person.rosterNumber.ifBlank { "—" }} • ${person.site.ifBlank { "غير محدد" }}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (roster.isEmpty()) item { Text("لا توجد أسماء مطابقة", Modifier.padding(12.dp)) }
                }
                Text("تاريخ التقرير", fontWeight = FontWeight.Bold)
                Text("الأيام دون حالة محفوظة تُحتسب «موجود» وفق نظام الكشف الحالي. الأشهر غير المؤرشفة لا تُحتسب.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(from) { from = it } }, modifier = Modifier.weight(1f)) {
                        Text("من: ${from.format(dateLabel)}", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { pick(to) { to = it } }, modifier = Modifier.weight(1f)) {
                        Text("إلى: ${to.format(dateLabel)}", fontSize = 12.sp)
                    }
                }
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Button(onClick = {
                    if (busy) return@Button
                    error = null
                    busy = true
                    val ids = selected.keys.toSet()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { loadReport(ids, from, to) } }
                        busy = false
                        result.onSuccess { report = it; mode = 2 }
                            .onFailure { error = it.localizedMessage ?: "تعذر إنشاء التقرير" }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy && selected.isNotEmpty() && !from.isAfter(to) && !to.isAfter(now)) {
                    if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Text("إنشاء التقرير")
                }
                Spacer(Modifier.height(8.dp))
            }
            2 -> report?.let { result ->
                Text("الفترة: ${result.start.format(dateLabel)} — ${result.end.format(dateLabel)}",
                    fontWeight = FontWeight.Bold)
                Text("عدد الافراد: ${result.rows.size} • مجموع أيام الحالات: ${result.totalDays}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.unavailableMonths.isNotEmpty()) {
                    Text("تنبيه: الأشهر التالية غير مؤرشفة ولم تُحتسب: ${result.unavailableMonths.joinToString()}",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Button(onClick = { onExcel(result) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("تصدير التقرير إلى Excel")
                }
                HorizontalDivider()
                val statuses = Attendance.entries
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)) {
                    item(key = "summary-title") { Text("ملخص الحالات حسب الفرد", fontWeight = FontWeight.Bold) }
                    item(key = "summary-heading") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(summaryHorizontal)) {
                            PeriodSummaryRow(listOf("الرقم (ع)", "الاسم", "الموقع", "الأيام") + statuses.map { it.label }, true)
                        }
                    }
                    items(result.rows, key = { "summary-${it.employee.id}" }, contentType = { "summary-row" }) { r ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(summaryHorizontal)) {
                            PeriodSummaryRow(listOf(r.employee.rosterNumber.ifBlank { "—" }, r.employee.name, r.employee.site,
                                r.days.size.toString()) + statuses.map { (r.counts[it.name] ?: 0).toString() }, false)
                        }
                    }
                    item { Text("التفاصيل بحسب الشهر واليوم", fontWeight = FontWeight.Bold) }
                    items(result.rows, key = { it.employee.id }) { r ->
                        var expanded by remember { mutableStateOf(false) }
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(r.employee.name, fontWeight = FontWeight.Bold)
                                Text("${r.employee.rosterNumber.ifBlank { "—" }} • ${r.employee.site} • ${r.days.size} يوم",
                                    fontSize = 12.sp)
                                r.byMonth.toSortedMap().forEach { (month, days) ->
                                    Text("${month.monthValue}/${month.year} — " +
                                        statuses.joinToString("  •  ") { "${it.label}: ${days.count { d -> d.status.name == it.name }}" },
                                        fontSize = 11.sp)
                                }
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) "إخفاء التفاصيل اليومية" else "عرض التفاصيل اليومية")
                                }
                                if (expanded) {
                                    r.days.sortedBy { it.date }.forEach { day ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${day.date.format(dateLabel)} (${day.date.format(weekDay)})", fontSize = 11.sp)
                                            Text(day.status.label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = colorFor(day.status))
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

