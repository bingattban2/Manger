package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manger.app.domain.*
import java.time.LocalDate

@Composable internal fun EmployeeEditor(
    old: Employee?, defaultCode: () -> String, onDismiss: () -> Unit,
    onSubmit: (Employee?, String, String, LocalDate, String, String) -> Unit,
    confirmLabel: String = "حفظ", confirmExitIfChanged: Boolean = false,
    availableSites: List<String> = emptyList(), knownEmployeeNames: List<String> = emptyList()
) {
    var name by remember(old) { mutableStateOf(old?.name ?: "") }
    var rosterNumber by remember(old) { mutableStateOf(old?.rosterNumber ?: "") }
    var code by remember(old) { mutableStateOf(old?.code ?: defaultCode()) }
    var site by remember(old) { mutableStateOf(old?.site ?: "") }
    var started by remember(old) { mutableStateOf(old?.started ?: LocalDate.now()) }
    var discardWarning by remember(old) { mutableStateOf(false) }
    val changed = old != null && (
        EmployeeOrder.clean(name) != old.name || code.trim() != old.code || rosterNumber.trim() != old.rosterNumber ||
            site.trim() != old.site || started != old.started
    )
    fun close() { if (confirmExitIfChanged && changed) discardWarning = true else onDismiss() }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(if (old == null) "إضافة فرد واحد" else "تعديل الفرد") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KnownRecordField(name, { name = it }, "الاسم الكامل", knownEmployeeNames,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rosterNumber, { rosterNumber = it }, label = { Text("الرقم (ع) — معرّف الكشف") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(code, { code = it }, label = { Text("الرقم الداخلي (للتوافق)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            KnownRecordField(site, { site = it }, "الموقع / موقع العمل", availableSites,
                modifier = Modifier.fillMaxWidth(), requireKnown = true)
            DateButton("تاريخ الالتحاق", started, onDate = { started = it },
                onClear = { started = LocalDate.now() })
        } },
        confirmButton = {
            TextButton(onClick = { onSubmit(old, name, code, started, site, rosterNumber) },
                enabled = name.isNotBlank() && code.isNotBlank() && rosterNumber.isNotBlank() && site.trim() in availableSites) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("إلغاء") } }
    )
    if (discardWarning) AlertDialog(
        onDismissRequest = { discardWarning = false },
        title = { Text("تعديل لم يتم اعتماده") },
        text = { Text("هل تريد إضافة التعديل إلى قائمة التغييرات قبل إغلاق النافذة؟") },
        confirmButton = {
            TextButton(onClick = {
                discardWarning = false
                onSubmit(old, name, code, started, site, rosterNumber)
            }, enabled = name.isNotBlank() && code.isNotBlank() && rosterNumber.isNotBlank() && site.trim() in availableSites) { Text("إضافة التعديل") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { discardWarning = false; onDismiss() }) { Text("تجاهل التعديل") }
                TextButton(onClick = { discardWarning = false }) { Text("متابعة") }
            }
        }
    )
}

  
