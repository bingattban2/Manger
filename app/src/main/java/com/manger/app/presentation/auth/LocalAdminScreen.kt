package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.manger.app.data.local.AttendanceDb
import com.manger.app.domain.LocalAccountInfo
import com.manger.app.domain.LocalRole
import com.manger.app.domain.LocalSession
import com.manger.app.domain.Employee
import com.manger.app.domain.Attendance
import com.manger.app.domain.RosterNumbers
import com.manger.app.domain.SupervisorPasswordPolicy

  
@Composable internal fun LocalAdminScreen(db: AttendanceDb, onBack: () -> Unit, onChanged: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val sites = remember(revision) { db.sites() }
    val people = remember(revision) { db.employees() }
    val supervisors = remember(revision) { db.accounts().filter { it.role == LocalRole.SUPERVISOR } }
    var editing by remember { mutableStateOf<LocalAccountInfo?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LocalAccountInfo?>(null) }
    var dutyEditing by remember { mutableStateOf<LocalAccountInfo?>(null) }
    var permissionTarget by remember { mutableStateOf<LocalAccountInfo?>(null) }
    var dutyStatus by remember { mutableStateOf(Attendance.PRESENT) }
    var feedback by remember { mutableStateOf<String?>(null) }
    fun execute(block: () -> Unit): Boolean {
        val result = runCatching(block)
        result.onSuccess { revision++; onChanged(); feedback = "تم حفظ التغييرات بنجاح" }
            .onFailure { feedback = "تعذرت العملية: ${it.localizedMessage ?: "خطأ غير معروف"}" }
        return result.isSuccess
    }
    Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("إدارة مشرفي المواقع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("المشرفون المسجلون: ${supervisors.size}", fontWeight = FontWeight.Bold)
                Text("المتصلون على هذا الجهاز الآن: ${db.supervisorsActiveOnThisDevice()}")
                Text("مهم: لا يمكن معرفة المتصلين حالياً عبر هواتف أخرى دون خادم ومزامنة مثل Supabase. هذا الرقم يخص الجهاز المحلي فقط.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة مشرف جديد") }
        if (feedback != null) Text(feedback.orEmpty(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(supervisors, key = { it.username }) { supervisor ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(supervisor.displayName, fontWeight = FontWeight.Bold)
                        Text("${supervisor.username} • ${supervisor.site}", style = MaterialTheme.typography.bodySmall)
                        Text("الرقم (ع): ${people.firstOrNull { it.id == supervisor.employeeId }?.let { it.rosterNumber.ifBlank { "غير مسجل" } } ?: "غير مرتبط"}",
                            style = MaterialTheme.typography.bodySmall)
                        val duty = remember(supervisor.username, supervisor.site, supervisor.enabled, revision) {
                            if (supervisor.site.isNullOrBlank()) null
                            else runCatching {
                                db.supervisorDuty(LocalSession(
                                    supervisor.username, supervisor.displayName,
                                    LocalRole.SUPERVISOR, supervisor.site
                                ))
                            }.getOrNull()
                        }
                        val dutyLabel = when {
                            supervisor.site.isNullOrBlank() -> "بدون موقع إشراف؛ يتطلب إعادة التعيين"
                            duty == null -> "تعذر التحقق من حالة الحضور"
                            duty.allowed -> "موجود ومعتمد"
                            else -> duty.message
                        }
                        Text("حالة اليوم: $dutyLabel",
                            color = if (duty?.allowed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { permissionTarget = supervisor }) {
                                Text(if (supervisor.enabled) "تعطيل الحساب" else "تفعيل الحساب",
                                    color = if (supervisor.enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row {
                            TextButton(onClick = { dutyStatus = Attendance.PRESENT; dutyEditing = supervisor },
                                enabled = supervisor.employeeId != null) { Text("تحضير اليوم") }
                            TextButton(onClick = { editing = supervisor }) { Text("تعديل") }
                            TextButton(onClick = { deleting = supervisor }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (supervisors.isEmpty()) item { Text("لا يوجد مشرفون مسجلون") }
        }
    }
    if (adding || editing != null) {
        val previous = editing
        key(previous?.username ?: "new") {
            SupervisorEditorDialog(previous, sites, people, onDismiss = { adding = false; editing = null },
                onSave = { username, name, pass, site, employeeId ->
                val success = execute {
                    if (previous == null) db.addSupervisor(username, name, pass, site, employeeId)
                    else db.updateSupervisor(previous.username, username, name, pass, site, employeeId)
                }
                if (success) { adding = false; editing = null }
            })
        }
    }
    if (permissionTarget != null) AlertDialog(
        onDismissRequest = { permissionTarget = null },
        title = { Text(if (permissionTarget!!.enabled) "تعطيل المشرف؟" else "تفعيل المشرف؟") },
        text = { Text("يُمنع الحساب المعطّل من تسجيل الدخول وحفظ أي تحضير جديد. عند الربط بالسيرفر تُطبَّق الصلاحية مركزياً.") },
        confirmButton = { TextButton(onClick = {
            val target = permissionTarget!!
            if (execute { db.setSupervisorEnabled(target.username,!target.enabled) }) permissionTarget = null
        }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = { permissionTarget = null }) { Text("إلغاء") } }
    )
    if (dutyEditing != null) AlertDialog(
        onDismissRequest = { dutyEditing = null },
        title = { Text("اعتماد حالة المشرف اليوم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${dutyEditing!!.displayName} • ${dutyEditing!!.site}")
                Text("اختر حالة اليوم. الحساب لا يستطيع تعديل التحضير إلا عندما تكون الحالة موجوداً ومسجلة فعلياً.",
                    style = MaterialTheme.typography.bodySmall)
                Attendance.entries.forEach { item ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = dutyStatus.name == item.name, onClick = { dutyStatus = item })
                        Text(item.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val account = dutyEditing!!
            if (execute { db.certifySupervisorToday(account.username, dutyStatus) }) dutyEditing = null
        }) { Text("اعتماد") } },
        dismissButton = { TextButton(onClick = { dutyEditing = null }) { Text("إلغاء") } }
    )
    if (deleting != null) AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text("تأكيد حذف المشرف") },
        text = { Text("حذف حساب «${deleting!!.displayName}»؟ ستبقى بيانات الموقع والافراد والتحضير دون تغيير.") },
        confirmButton = { TextButton(onClick = {
            val account = deleting!!
            if (execute { db.removeSupervisor(account.username) }) deleting = null
        }) { Text("تأكيد الحذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("تراجع") } }
    )
}

  
@Composable private fun SupervisorEditorDialog(
    original: LocalAccountInfo?, sites: List<String>, people: List<Employee>, onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long) -> Unit
) {
    var username by remember(original) { mutableStateOf(original?.username.orEmpty()) }
    var site by remember(original) { mutableStateOf(original?.site ?: sites.firstOrNull().orEmpty()) }
    val candidates = remember(people) {
        people.filter { it.active && it.ended == null &&
            !it.started.isAfter(java.time.LocalDate.now()) }
    }
    var employeeId by remember(original) { mutableStateOf(original?.employeeId) }
    var displayName by remember(original, people) {
        mutableStateOf(people.firstOrNull { it.id == original?.employeeId }?.name ?: original?.displayName.orEmpty())
    }
    var password by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val matchedEmployee = candidates.firstOrNull { it.id == employeeId && it.name == displayName.trim() }
    val query = remember(displayName) { RosterNumbers.searchKey(displayName) }
    val suggestions = remember(candidates, query) {
        if (query.isBlank()) candidates.take(8)
        else candidates.asSequence().filter { RosterNumbers.searchKey(it.name).contains(query) ||
            RosterNumbers.searchKey(it.rosterNumber).contains(query) }.take(8).toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "إضافة مشرف جديد" else "تعديل المشرف") },
        text = {
            Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم المستخدم (بالإنجليزية)") }, singleLine = true)
                OutlinedTextField(
                    displayName,
                    onValueChange = {
                        displayName = it
                        employeeId = null
                        showSuggestions = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم المشرف — من الكشف العام") },
                    placeholder = { Text("اكتب الاسم لتظهر أسماء الافراد") },
                    singleLine = true,
                    trailingIcon = { Text(if (matchedEmployee != null) "✓" else "⌕") }
                )
                if (showSuggestions && candidates.isNotEmpty()) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState())) {
                            suggestions.forEach { employee ->
                                Column(Modifier.fillMaxWidth().clickable {
                                    employeeId = employee.id
                                    displayName = employee.name
                                    showSuggestions = false
                                }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(employee.name, fontWeight = FontWeight.SemiBold)
                                    Text("الرقم (ع): ${employee.rosterNumber.ifBlank { "—" }} • الموقع الوظيفي: ${employee.site.ifBlank { "بدون موقع" }}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider()
                            }
                            if (suggestions.isEmpty()) Text("لا توجد أسماء مطابقة في الكشف العام",
                                Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (matchedEmployee != null) {
                    Text("تم الربط بكشف الفرد: ${matchedEmployee.name} • الرقم (ع): ${matchedEmployee.rosterNumber.ifBlank { "غير محدد" }}",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                } else Text("يجب اختيار الاسم من الاقتراحات لربطه بسجل حضور محدد؛ كتابة الاسم وحدها لا تكفي.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (original == null) "كلمة السر" else "كلمة السر الجديدة (اتركها فارغة للإبقاء عليها)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("الموقع المرتبط: ${site.ifBlank { "اختر موقعاً" }} ▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sites.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = {
                            site = option
                            expanded = false
                        }) }
                    }
                }
                if (password.isNotBlank() && !SupervisorPasswordPolicy.isValid(password)) {
                    Text(SupervisorPasswordPolicy.guidance, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (candidates.isEmpty()) Text("لا يوجد أفراد نشطون في الكشف العام؛ أضف المشرف إلى الكشف أولاً.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (sites.isEmpty()) Text("أنشئ موقعاً أولاً من إدارة الحالات والمواقع", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                matchedEmployee?.let { onSave(username, it.name, password, site, it.id) }
            }, enabled = username.isNotBlank() && site in sites && matchedEmployee != null &&
                    (if (original == null) SupervisorPasswordPolicy.isValid(password)
                        else password.isEmpty() || SupervisorPasswordPolicy.isValid(password))) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
