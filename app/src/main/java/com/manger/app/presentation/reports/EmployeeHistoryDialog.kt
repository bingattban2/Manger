package com.manger.app.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.manger.app.domain.*

@Composable internal fun EmployeeHistoryDialog(e: Employee, version: Int, loadHistory: suspend (Long) -> List<EmployeeMonthReport>, onDismiss: () -> Unit) {
    var loaded by remember(e.id, version) { mutableStateOf<List<EmployeeMonthReport>?>(null) }
    var loadError by remember(e.id, version) { mutableStateOf<String?>(null) }
    LaunchedEffect(e.id, version) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { loadHistory(e.id) }
        }
        result.onSuccess { loaded = it }.onFailure { loadError = it.localizedMessage ?: "تعذر تحميل التقرير" }
    }
    val reports = loaded.orEmpty()
    val totals = remember(reports) {
        reports.flatMap { it.totals.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.84f), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("التقرير الشامل: ${e.name}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("الرقم: ${e.rosterNumber.ifBlank { "—" }}  •  الموقع: ${e.site.ifBlank { "غير محدد" }}", fontSize = 12.sp)
                if (loaded == null && loadError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (loadError != null) Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                Text("إجمالي الأيام المحتسبة: ${reports.sumOf { it.days }}", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Attendance.entries.forEach { state ->
                        Text("${state.label}: ${totals[state.name] ?: 0}", color = colorFor(state),
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reports.asReversed(), key = { it.month.toString() }) { month ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(month.month.format(monthFormat), fontWeight = FontWeight.Bold)
                                Text("أيام الشهر المحتسبة: ${month.days}", fontSize = 12.sp)
                                Attendance.entries.forEach { state ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(state.label, color = colorFor(state), fontSize = 12.sp)
                                        Text("${month.totals[state.name] ?: 0} يوم", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (reports.isEmpty()) item { Text("لا توجد بيانات تاريخية متاحة لهذا الفرد") }
                }
                Text("الأشهر السابقة من النسخ المؤرشفة فقط؛ والشهر الجاري حتى تاريخ اليوم.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("إغلاق") }
            }
        }
    }
}

  
