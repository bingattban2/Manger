package com.manger.app.presentation.monthly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.manger.app.data.export.RosterBatchExporter

  
@Composable internal fun ExportRosterDialog(
    format: RosterBatchExporter.Format,
    sites: List<String>,
    folder: String,
    busy: Boolean,
    progress: String,
    progressFraction: Float,
    onPickFolder: () -> Unit,
    onDefaultFolder: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (RosterBatchExporter.Choice) -> Unit
) {
    val selected = remember(sites) { mutableStateMapOf<String, Boolean>() }
    var full by remember { mutableStateOf(true) }
    var cancelConfirm by remember { mutableStateOf(false) }
    val all = full && sites.all { selected[it] == true }
    val count = (if (full) 1 else 0) + selected.values.count { it }
    Dialog(onDismissRequest = { if (!busy) cancelConfirm = true },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تصدير الكشوف بصيغة ${format.extension.uppercase()}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("مسار التصدير", style = MaterialTheme.typography.titleMedium)
                Text(folder, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onPickFolder, enabled = !busy) { Text("تغيير المجلد") }
                    TextButton(onClick = onDefaultFolder, enabled = !busy) { Text("المجلد التلقائي") }
                }
                HorizontalDivider()
                Text("الكشف المصدر", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = all, enabled = !busy, onCheckedChange = { enabled ->
                        full = enabled
                        sites.forEach { selected[it] = enabled }
                    })
                    Text("كل الكشوفات (الكلي + ${sites.size} موقع)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = full, enabled = !busy, onCheckedChange = { full = it })
                    Text("الكشف الكلي للقوة")
                }
                Text("كشوف المواقع المسجلة (${sites.size})", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(sites, key = { it }) { site ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected[site] == true, enabled = !busy,
                                onCheckedChange = { selected[site] = it })
                            Text("كشف موقع $site", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (busy) {
                    Text(progress, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                } else {
                    Text("عدد الملفات المختارة: $count", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { cancelConfirm = true }, enabled = !busy,
                        modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(onClick = {
                        onConfirm(RosterBatchExporter.Choice(full, selected.filterValues { it }.keys.toSet()))
                    }, enabled = !busy && count > 0, modifier = Modifier.weight(1f)) {
                        Text("تأكيد التصدير")
                    }
                }
            }
        }
    }
    if (cancelConfirm) AlertDialog(
        onDismissRequest = { cancelConfirm = false },
        title = { Text("إلغاء التصدير") },
        text = { Text("هل تريد إغلاق إعدادات التصدير دون إنشاء أي ملف؟") },
        confirmButton = { TextButton(onClick = { cancelConfirm = false; onCancel() }) { Text("تأكيد الإلغاء") } },
        dismissButton = { TextButton(onClick = { cancelConfirm = false }) { Text("تراجع") } }
    )
}
