package com.manger.app.presentation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PeriodSummaryRow(values: List<String>, title: Boolean) {
    Row(Modifier.background(if (title) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.width(when (index) { 1 -> 140.dp; 2 -> 100.dp; else -> 68.dp })
                .padding(horizontal = 4.dp, vertical = 9.dp),
                fontWeight = if (title) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp, maxLines = 2)
        }
    }
    HorizontalDivider()
}
