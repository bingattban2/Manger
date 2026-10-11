package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

  
@Composable
internal fun ManagementHome(
    employeeCount: Int,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onReport: () -> Unit,
    onStatus: () -> Unit,
    onSupervisors: () -> Unit,
    onCompose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManagementTile("تعديل الكشوفات", "إضافة كشف أو حذف كشوف وتعديل الأعمدة", "▦", onAdd, Modifier.weight(1f))
            ManagementTile("تعديل القوة", "تحرير الأسماء والأرقام والمواقع وحفظ التعديلات", "✎", onEdit, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManagementTile("التقرير", "تقرير دوام وإجازات كل فرد عبر الأشهر", "▤", onReport, Modifier.weight(1f))
            ManagementTile("إدارة الحالات والمواقع", "تخصيص الحالات وإدارة المواقع", "◉", onStatus, Modifier.weight(1f))
        }
        OutlinedButton(onClick = onSupervisors, modifier = Modifier.fillMaxWidth()) { Text("إدارة مشرفي المواقع  ◉") }
        Button(onClick = onCompose, modifier = Modifier.fillMaxWidth()) { Text("إرسال إشعار للمشرفين  ✉") }
        Text("إجمالي الافراد المسجلين: $employeeCount",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManagementTile(
    title: String, description: String, glyph: String,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    ElevatedCard(onClick = onClick, modifier = modifier.aspectRatio(1f)) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(glyph, fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(description, fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
