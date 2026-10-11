package com.manger.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.manger.app.domain.*
import java.time.LocalDate

@Composable internal fun AttendanceEditor(
    people: List<Employee>, saving: Boolean, errorMessage: String?,
    onDismiss: () -> Unit, onSave: (List<AttendanceBatch>) -> Unit, isAdmin: Boolean
) {
    var search by remember { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var chosenSite by remember { mutableStateOf<String?>(null) }
    var siteExpanded by remember { mutableStateOf(false) }
    val employeeNames = remember(people) { people.map { it.name }.distinct() }
    val sites = remember(people) {
        people.filter { it.active }.map { it.site.trim() }.filter { it.isNotBlank() }.distinct()
            .sortedWith(java.text.Collator.getInstance(java.util.Locale.forLanguageTag("ar")))
    }
    val selected = remember { mutableStateMapOf<Long, Boolean>() }
    val queued = remember { mutableStateListOf<AttendanceBatch>() }
    var status by remember { mutableStateOf(Attendance.fromStored("ABSENT")) }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    val start = from ?: to ?: LocalDate.now()
    val end = to ?: from ?: LocalDate.now()
    val datesValid = !end.isBefore(start)
    val candidates = remember(people, chosenSite, search) {
        people.filter { it.active && (chosenSite == null || it.site.trim() == chosenSite) &&
            it.matchesSearch(search) }
    }
    val currentGroup = if (selected.isNotEmpty() && datesValid)
        AttendanceBatch(selected.keys.toSet(), status, start, end) else null
    val groupsToApply = queued.toList() + listOfNotNull(currentGroup)
    val distinctEmployees = groupsToApply.flatMap { it.employeeIds }.distinct().size

    Dialog(onDismissRequest = { if (!saving) confirmCancel = true }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("التحضير الجماعي السريع", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    if (isAdmin) "اختر الأسماء من أي موقع بحسب الفلتر، ثم حدد الحالة والتواريخ." else "اختر الأسماء من موقعك فقط، ثم حدد الحالة والتواريخ.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KnownRecordField(search, { search = it }, title = "الاسم أو الرقم (ع)",
                        options = employeeNames,
                        modifier = Modifier.weight(1f))
                    if (isAdmin) Box(Modifier.width(125.dp)) {
                        OutlinedButton(onClick = { siteExpanded = true }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("${chosenSite?.ifBlank { "غير محدد" } ?: "الكل"} ▾",
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = siteExpanded, onDismissRequest = { siteExpanded = false }) {
                            DropdownMenuItem(text = { Text("الكل • جميع المواقع") }, onClick = {
                                chosenSite = null; siteExpanded = false
                            })
                            sites.forEach { site ->
                                DropdownMenuItem(text = { Text(site.ifBlank { "غير محدد" }) }, onClick = {
                                    chosenSite = site; siteExpanded = false
                                })
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(contentPadding = PaddingValues(4.dp)) {
                        items(candidates, key = { it.id }) { employee ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (selected.containsKey(employee.id)) selected.remove(employee.id)
                                    else selected[employee.id] = true
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    selected.containsKey(employee.id),
                                    onCheckedChange = { checked ->
                                        if (checked) selected[employee.id] = true
                                        else selected.remove(employee.id)
                                    }
                                )
                                Text(
                                    "${employee.rosterNumber.ifBlank { "—" }} — ${employee.name} • ${employee.site.ifBlank { "غير محدد" }}",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Text("الحالة المطلوبة • يمكن اعتماد جميع الأسماء المحددة معاً", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Attendance.entries.forEach { type ->
                        FilterChip(
                            selected = status.name == type.name,
                            onClick = { if (AttendanceLeavePolicy.maySelectStatus(isAdmin, type.name)) status = type },
                            enabled = AttendanceLeavePolicy.maySelectStatus(isAdmin, type.name),
                            label = { Text(type.label, fontSize = 10.sp, maxLines = 1) },
                        )
                    }
                }
                if (!isAdmin) Text("حالة إجازة للإدارة العامة فقط، ولا يمكن للمشرف إلغاء إجازة قائمة.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        DateButton("من تاريخ", from, onDate = { from = it }, onClear = { from = null })
                    }
                    Box(Modifier.weight(1f)) {
                        DateButton("إلى تاريخ", to, onDate = { to = it }, onClear = { to = null })
                    }
                }
                if (!datesValid) Text("تاريخ النهاية يسبق البداية", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                OutlinedButton(
                    onClick = {
                        queued.add(AttendanceBatch(selected.keys.toSet(), status, start, end))
                        selected.clear()
                        search = ""
                    },
                    enabled = !saving && selected.isNotEmpty() && datesValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إضافة المجموعة لقائمة الاعتماد (${selected.size} فرد)", fontSize = 12.sp)
                }
                if (queued.isNotEmpty()) {
                    Text("المجموعات المجهزة (${queued.size}) — يمكن حذف أي مجموعة قبل الاعتماد", fontSize = 11.sp)
                    Column(Modifier.fillMaxWidth().heightIn(max = 92.dp).verticalScroll(rememberScrollState())) {
                        queued.forEachIndexed { index, batch ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}. ${batch.status.label} • ${batch.employeeIds.size} فرد • ${batch.start} ← ${batch.end}",
                                    modifier = Modifier.weight(1f), fontSize = 11.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { queued.removeAt(index) }) { Text("حذف", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                Text("الأشهر المؤرشفة للعرض فقط، ولا تُعدّل حالات أيامها.", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (errorMessage != null) Text(errorMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { confirmCancel = true }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(
                        onClick = { confirmApply = true },
                        enabled = !saving && groupsToApply.isNotEmpty() && (selected.isEmpty() || datesValid),
                        modifier = Modifier.weight(1.6f)
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("موافق ($distinctEmployees)", maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("إلغاء التحضير الجماعي؟") },
        text = { Text("سيتم التراجع عن جميع الاختيارات والمجموعات التي لم تُعتمد. هل تريد الإلغاء؟") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onDismiss() }) {
            Text("تأكيد الإلغاء", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("العودة للتحضير") } }
    )
    if (confirmApply) AlertDialog(
        onDismissRequest = { if (!saving) confirmApply = false },
        title = { Text("مراجعة واعتماد التحضير") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("يرجى مراجعة الأسماء والحالة والتواريخ قبل الحفظ:", fontWeight = FontWeight.Bold)
            groupsToApply.forEachIndexed { index, group ->
                Text("مجموعة ${index + 1} • ${group.status.label} • من ${group.start} إلى ${group.end}",
                    fontWeight = FontWeight.SemiBold)
                group.employeeIds.mapNotNull { id -> people.firstOrNull { it.id == id } }
                    .sortedBy { it.name }.forEach { employee ->
                        Text("• ${employee.name} — ${employee.site.ifBlank { "غير محدد" }}",
                            style = MaterialTheme.typography.bodySmall)
                    }
            }
        } },
        confirmButton = { TextButton(onClick = { if (!saving) onSave(groupsToApply) }, enabled = !saving) {
            Text(if (saving) "جارٍ الحفظ" else "تأكيد الحفظ")
        } },
        dismissButton = { TextButton(onClick = { confirmApply = false }, enabled = !saving) { Text("رجوع دون حفظ") } }
    )
}
