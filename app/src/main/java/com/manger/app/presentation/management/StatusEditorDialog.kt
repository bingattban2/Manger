package com.manger.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manger.app.domain.*

@Composable internal fun StatusEditorDialog(
    original: Attendance?, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit
) {
    var title by remember(original) { mutableStateOf(original?.label.orEmpty()) }
    var symbol by remember(original) { mutableStateOf(original?.symbol.orEmpty()) }
    var chosen by remember(original) { mutableStateOf(original?.argb ?: 0xFF8E44ADL) }
    var hex by remember(original) {
        mutableStateOf("%06X".format(java.util.Locale.ROOT, chosen and 0xFFFFFFL))
    }
    val palette = listOf(0xFF21885AL, 0xFFCA4350L, 0xFF3477CBL, 0xFFBA8317L,
        0xFF8E44ADL, 0xFF00838FL, 0xFF6D4C41L, 0xFF546E7AL, 0xFFE65100L,
        0xFF3949ABL, 0xFFAD1457L, 0xFF455A64L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "إضافة حالة" else "تعديل حالة: ${original.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it.take(32) }, label = { Text("اسم الحالة") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(symbol, { symbol = it.take(3) }, label = { Text("رمز الحالة") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("اختر اللون", fontWeight = FontWeight.SemiBold)
                palette.chunked(6).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { color ->
                            Box(Modifier.weight(1f).height(33.dp)
                                .background(Color(color.toInt()), RoundedCornerShape(8.dp))
                                .border(if (chosen == color) 3.dp else 0.dp,
                                    if (chosen == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    RoundedCornerShape(8.dp))
                                .clickable {
                                    chosen = color
                                    hex = "%06X".format(java.util.Locale.ROOT, color and 0xFFFFFFL)
                                }, contentAlignment = Alignment.Center) {
                                if (chosen == color) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                OutlinedTextField(hex, { input ->
                    hex = input.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
                        .take(6).uppercase(java.util.Locale.ROOT)
                    if (hex.length == 6) chosen = 0xFF000000L or hex.toLong(16)
                }, label = { Text("اللون بصيغة HEX (ست خانات)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("المعاينة: $title  ($symbol)", color = Color(chosen.toInt()),
                    fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, symbol, chosen) },
            enabled = title.isNotBlank() && symbol.isNotBlank() && hex.length == 6) { Text("حفظ وتطبيق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

