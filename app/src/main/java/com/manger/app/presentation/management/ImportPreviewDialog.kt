package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.*

  
@Composable internal fun ImportPreviewDialog(
    sheets: Map<String, List<ImportedEmployee>>, selected: Set<String>,
    onToggle: (String) -> Unit, check: Result<ImportCheck>?, saving: Boolean,
    progressText: String, progress: Float,
    onImport: () -> Unit, onDismiss: () -> Unit
) {
    val total = sheets.filterKeys { it in selected }.values.sumOf { it.size }
    val success = check?.getOrNull()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("مراجعة استيراد الكشف") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("اختر الأوراق المطلوب استيرادها، ثم راجع الأرقام قبل الحفظ.", fontSize = 12.sp)
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                sheets.forEach { (title, rows) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = title in selected, onCheckedChange = { if (!saving) onToggle(title) }, enabled = !saving)
                        Text("$title — ${rows.size} فرد", fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider()
            Text("الأسماء المحددة: $total")
            if (saving) {
                Text(progressText, fontSize = 12.sp)
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            if (check == null && total > 0) Text("جارٍ فحص الكشف...", fontSize = 12.sp)
            check?.exceptionOrNull()?.let { Text(it.localizedMessage ?: "بيانات الاستيراد غير صالحة", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (success != null) {
                Text("أفراد جدد: ${success.added} • تعديلات: ${success.updated} • دون تغيير: ${success.unchanged}", fontSize = 12.sp)
                if (success.warnings.isNotEmpty()) {
                    Text("مراجعة تغييرات الاسم أو الموقع للأرقام الموجودة:", fontSize = 12.sp)
                    Column(Modifier.heightIn(max = 110.dp).verticalScroll(rememberScrollState())) {
                        success.warnings.forEach { Text(it, fontSize = 11.sp) }
                    }
                }
            }
            Text("يُطابق الافراد بالرقم (ع) فقط. تكرار الاسم مسموح، والرقم المكرر يوقف الاستيراد بالكامل.", fontSize = 11.sp)
        } },
        confirmButton = { TextButton(onClick = onImport, enabled = !saving && success != null && total > 0 && success.added + success.updated > 0) { Text("اعتماد وحفظ") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } }
    )
}
