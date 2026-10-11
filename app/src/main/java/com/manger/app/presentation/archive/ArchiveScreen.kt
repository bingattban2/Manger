package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.*
import java.time.YearMonth

@Composable internal fun ArchiveScreen(
    months: List<YearMonth>, selected: YearMonth?, selectedData: MonthData?,
    onSelect: (YearMonth) -> Unit, onBack: () -> Unit,
    onExcel: () -> Unit, onPdf: () -> Unit, siteScope: String? = null
) {
    if (selected != null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 10.dp)) {
                Text("→ العودة إلى قائمة الأرشيف")
            }
            if (selectedData != null) {
                
                if (siteScope != null) Text("أرشيف موقع الحساب: $siteScope", modifier = Modifier.padding(horizontal = 14.dp))
                Box(Modifier.weight(1f)) { MonthlyScreen(selectedData, { _, _ -> onExcel() }, { _, _ -> onPdf() }, archived = true, allowExport = siteScope == null) }
            } else {
                Text("تعذر قراءة كشف الشهر المؤرشف", Modifier.padding(16.dp))
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("أرشيف الأشهر الماضية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("تُحفظ الأشهر المكتملة تلقائياً عندما يكون التطبيق مفتوحاً أو عند فتحه بعد بداية شهر جديد. كل كشف مؤرشف ثابت وللقراءة فقط.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (months.isEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text("لا يوجد شهر مكتمل مؤرشف حتى الآن. سيظهر أول كشف هنا مع بداية الشهر التالي.",
                        Modifier.padding(18.dp))
                }
            } else {
                Text("${months.size} شهر مؤرشف", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 95.dp)) {
                    items(months, key = { it.toString() }) { month ->
                        ElevatedCard(onClick = { onSelect(month) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("▦", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(month.format(monthFormat), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(if (siteScope == null) "كشف شهري محفوظ • عرض وتصدير" else "كشف شهري محفوظ • عرض فقط", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

