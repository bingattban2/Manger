package com.manger.app.presentation

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.*
import java.time.LocalDate

@Composable internal fun EmployeesScreen(
    employees: List<Employee>, mode: Int, drafts: Map<Long, Employee>, saving: Boolean,
    onModeChange: (Int) -> Unit, onExit: () -> Unit,
    onNew: () -> Unit, onImport: () -> Unit,
    onStage: (Employee) -> Unit, onSave: () -> Unit, onReport: (Employee) -> Unit,
    statuses: List<Attendance>, onStatusAdd: () -> Unit, onStatusEdit: (Attendance) -> Unit,
    onDelete: (Employee) -> Unit,
    siteDetails: List<LocalSiteInfo>,
    onSiteAdd: (String) -> Boolean,
    onSiteRename: (String, String) -> Boolean,
    onSiteDelete: (String) -> Boolean,
    onSupervisorManage: () -> Unit,
    onMessageCompose: () -> Unit,
    onReportPage: () -> Unit,
    onDeleteSiteRosters: (Set<String>) -> Unit, deletingRosters: Boolean,
    rosterColumns: List<RosterColumn>, onColumnsSave: (List<RosterColumn>) -> Unit,
    customColumnValues: Map<Pair<Long,String>,String>, onCustomValueSave: (Long,String,String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(true) }
    var filterSite by remember { mutableStateOf<String?>(null) }
    var filterOpened by remember { mutableStateOf(false) }
    var sortBySite by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Employee?>(null) }
    var deleteTarget by remember { mutableStateOf<Employee?>(null) }
    var customTarget by remember { mutableStateOf<Employee?>(null) }
    val employeeNames = remember(employees) { employees.map { it.name }.distinct() }
    val finalRoster = remember(employees, drafts.toMap()) {
        EmployeeOrder.employees(employees.map { drafts[it.id] ?: it })
    }
    val filtered = EmployeeRosterFilter.select(finalRoster, search, showArchived, filterSite, sortBySite)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != 0) TextButton(onClick = onExit, enabled = !saving) { Text("→ رجوع") }
            Text("إدارة الافراد", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        when (mode) {
            0 -> ManagementHome(
                employeeCount = employees.size,
                onAdd = { onModeChange(1) },
                onEdit = { onModeChange(2) },
                onReport = onReportPage,
                onStatus = { onModeChange(4) },
                onSupervisors = onSupervisorManage,
                onCompose = onMessageCompose
            )
            1 -> {
                Text("تعديل الكشوفات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                ElevatedCard(onClick = { onModeChange(7) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("إضافة كشف", fontWeight = FontWeight.Bold)
                        Text("إضافة فرد واحد أو استيراد كشف Excel وCSV", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ElevatedCard(onClick = { onModeChange(8) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("تعديل الكشوفات", fontWeight = FontWeight.Bold)
                        Text("حذف كشوف مواقع محددة أو تخصيص الأعمدة الثابتة للكشف", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            7 -> {
                Text("إضافة كشف", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة فرد واحد") }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("إضافة كشف Excel أو CSV") }
                Text("الأعمدة المطلوبة: الرقم (ع)، الاسم، الموقع. لا يُدمج الافراد بمجرد تشابه الأسماء.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            8 -> {
                Text("تعديل الكشوفات", style = MaterialTheme.typography.titleMedium)
                ElevatedCard(onClick = { onModeChange(10) }, modifier = Modifier.fillMaxWidth()) {
                    Text("حذف كشف موقع واحد أو عدة مواقع", Modifier.padding(19.dp))
                }
                ElevatedCard(onClick = { onModeChange(9) }, modifier = Modifier.fillMaxWidth()) {
                    Text("التعديل على هيكل الكشف (الأعمدة الثابتة)", Modifier.padding(19.dp))
                }
            }
            9 -> RosterColumnsEditor(rosterColumns,onColumnsSave)
            10 -> SiteRosterDeleteScreen(siteDetails,onDeleteSiteRosters,deletingRosters)
            4 -> {
                Text("إدارة الحالات والمواقع", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                ElevatedCard(onClick = { onModeChange(5) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("إدارة الحالات", fontWeight = FontWeight.Bold)
                        Text("إضافة الحالات وتغيير الأسماء والرموز والألوان", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ElevatedCard(onClick = { onModeChange(6) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("إدارة المواقع", fontWeight = FontWeight.Bold)
                        Text("إضافة المواقع وتعديل أسمائها وحذف المواقع غير المرتبطة", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            6 -> LocalSiteManagerScreen(siteDetails, onSiteAdd, onSiteRename, onSiteDelete)
            5 -> {
                Text("التحكم في الحالات", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Button(onClick = onStatusAdd, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة حالة جديدة") }
                Text("تعديل الاسم أو الرمز أو اللون لا يغيّر حالة الفرد المحفوظة، بل طريقة عرضها في جميع الكشوف والتقارير.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 92.dp)) {
                    items(statuses, key = { it.name }) { state ->
                        ElevatedCard(onClick = { onStatusEdit(state) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).background(colorFor(state), RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text(state.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(state.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text("تعديل", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            2 -> {
                Text("الكشف الكلي — تعديل بيانات الافراد", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التغييرات غير المحفوظة: ${drafts.size}", modifier = Modifier.weight(1f),
                        fontSize = 12.sp, color = if (drafts.isNotEmpty()) gold else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onSave, enabled = drafts.isNotEmpty() && !saving) {
                        if (saving) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Text("حفظ التعديلات")
                    }
                }
                EmployeeRosterSearchBar(search,{ search=it },sortBySite,filterSite,
                    siteDetails.map { it.name } + if (finalRoster.any { it.site.isBlank() }) listOf("") else emptyList(),
                    employeeNames,onSort={ sortBySite=it },onSite={ filterSite=it })
                Text("العرض: ${filterSite ?: if (sortBySite) "حسب الموقع" else "أبجدياً"}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(showArchived, onCheckedChange = { showArchived = it })
                    Text("إظهار الافراد المؤرشفين", fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} فرد", fontSize = 12.sp)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 94.dp)) {
                    itemsIndexed(filtered, key = { _, e -> e.id }) { index, employee ->
                        EmployeeRosterRow(employee=employee,index=index,changed=drafts.containsKey(employee.id),
                            saving=saving,hasCustom=rosterColumns.any { it.custom },
                            onEdit={ editing=employee },onCustom={ customTarget=employee },
                            onReport={ onReport(employee) },onDelete={ deleteTarget=employee },
                            onArchive={ onStage(employee.copy(active=!employee.active,
                                ended=if(employee.active) LocalDate.now() else null)) })
                    }
                    if (filtered.isEmpty()) item { Text("لا توجد نتائج", modifier = Modifier.padding(22.dp)) }
                }
            }
        }
    }
    if (deleteTarget != null && mode == 2) {
        val target = deleteTarget!!
        AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الفرد نهائياً؟") },
            text = { Text("سيُحذف ${target.name} وسجل تحضيره الجاري نهائياً. الأرشيف الشهري المحفوظ سابقاً لن يتغير. يوصى بإنشاء نسخة احتياطية أولاً.") },
            confirmButton = { TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("تأكيد الحذف") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } })
    }
    if (editing != null && mode == 2) {
        val target = editing!!
        EmployeeEditor(
            old = target, defaultCode = { target.code }, onDismiss = { editing = null },
            confirmLabel = "إضافة إلى التعديلات", confirmExitIfChanged = true,
            availableSites = siteDetails.map { it.name },
            knownEmployeeNames = employees.map { it.name },
            onSubmit = { _, name, code, started, site, rosterNumber ->
                onStage(target.copy(
                    name = EmployeeOrder.clean(name), code = code.trim(), rosterNumber = rosterNumber.trim(),
                    started = started, site = site.trim().take(120)
                ))
                editing = null
            }
        )
    }
    if (customTarget != null && mode == 2) {
        val target=customTarget!!
        var customDraft by remember(target, customColumnValues) { mutableStateOf(
            rosterColumns.filter { it.custom }.associate { it.key to customColumnValues[target.id to it.key].orEmpty() }
        ) }
        AlertDialog(onDismissRequest={customTarget=null},title={Text("أعمدة إضافية — ${target.name}")},
            text={Column(Modifier.heightIn(max=400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                rosterColumns.filter { it.custom }.forEach { col ->
                    OutlinedTextField(customDraft[col.key].orEmpty(),onValueChange={ value ->
                        customDraft = customDraft + (col.key to value.take(200))
                    },label={Text(col.title)},modifier=Modifier.fillMaxWidth())
                }
            }},
            confirmButton={TextButton(onClick={
                val original=rosterColumns.filter { it.custom }.associate { it.key to customColumnValues[target.id to it.key].orEmpty() }
                customDraft.filter { (key,value) -> value!=original[key] }.forEach { (key,value) ->
                    onCustomValueSave(target.id,key,value)
                }
                customTarget=null
            }) {Text("حفظ البيانات")}},
            dismissButton={TextButton(onClick={customTarget=null}) {Text("إلغاء")}})
    }

}

