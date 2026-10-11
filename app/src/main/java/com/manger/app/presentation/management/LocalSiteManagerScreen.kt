package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manger.app.domain.LocalSiteInfo

  
@Composable internal fun LocalSiteManagerScreen(
    sites: List<LocalSiteInfo>,
    onAdd: (String) -> Boolean,
    onRename: (String, String) -> Boolean,
    onDelete: (String) -> Boolean
) {
    var chosenName by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var selectedSiteForEdit by remember { mutableStateOf("") }
    val chosen = sites.firstOrNull { it.name == chosenName }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("إدارة المواقع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = { draft = ""; creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة موقع جديد") }
        Text("المواقع المسجلة: ${sites.size} • اضغط على موقع لعرض تفاصيله", style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(sites, key = { it.name }) { site ->
                ElevatedCard(onClick = { chosenName = site.name }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(site.name, fontWeight = FontWeight.Bold)
                        Text("الافراد المرتبطون: ${site.totalEmployees} • النشطون: ${site.activeEmployees}", style = MaterialTheme.typography.bodySmall)
                        Text("المشرف: ${site.supervisors.joinToString("، ").ifBlank { "لا يوجد مشرف" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (sites.isEmpty()) item { Text("لا توجد مواقع بعد") }
        }
    }
    if (chosen != null) AlertDialog(
        onDismissRequest = { chosenName = null },
        title = { Text(chosen.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إجمالي الافراد المرتبطين: ${chosen.totalEmployees}")
            Text("الافراد النشطون: ${chosen.activeEmployees}")
            Text("المشرفون: ${chosen.supervisors.joinToString("، ").ifBlank { "لم يتم تعيين مشرف" }}")
        } },
        confirmButton = { TextButton(onClick = { draft = chosen.name; selectedSiteForEdit = chosen.name; chosenName = null; editing = true }) { Text("تعديل الاسم") } },
        dismissButton = {
            Row {
                TextButton(onClick = { chosenName = null; deleting = true; draft = chosen.name }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { chosenName = null }) { Text("إغلاق") }
            }
        }
    )
    if (creating || editing) AlertDialog(
        onDismissRequest = { creating = false; editing = false },
        title = { Text(if (creating) "إضافة موقع جديد" else "تعديل اسم الموقع") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KnownRecordField(value = draft, onValueChange = { draft = it }, title = "اسم الموقع",
                options = sites.map { it.name }, modifier = Modifier.fillMaxWidth())
            if (draft.trim() in sites.map { it.name } && creating) {
                Text("الموقع مسجل بالفعل. اختر اسماً مختلفاً", color = MaterialTheme.colorScheme.error)
            }
            if (editing) Text("سيُحدّث ربط الافراد والمشرفين تلقائياً. تبقى الأرشيفات التاريخية محفوظة بأسمائها الأصلية.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(enabled = draft.isNotBlank() && (!creating || draft.trim() !in sites.map { it.name }), onClick = {
            if (creating) { if (onAdd(draft)) { creating = false; draft = "" } }
            else if (editing) {
                
                if (onRename(selectedSiteForEdit, draft)) { editing = false; draft = "" }
            }
        }) { Text(if (creating) "موافق" else "حفظ") } },
        dismissButton = { TextButton(onClick = { creating = false; editing = false }) { Text("إلغاء") } }
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("تأكيد حذف الموقع") },
        text = { Text("هل تريد حذف موقع «$draft»؟ سيبقى أفراده في الكشف العام دون موقع، وتُعطّل حسابات مشرفيه حتى تعيد الإدارة تعيينهم. لن تُحذف سجلات التحضير أو الأرشيف.") },
        confirmButton = { TextButton(onClick = { if (onDelete(draft)) { deleting = false; draft = "" } }) {
            Text("تأكيد الحذف", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("تراجع") } }
    )
}
