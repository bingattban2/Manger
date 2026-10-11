package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.manger.app.domain.*

@Composable internal fun AuditDialog(events: List<Audit>, people:List<Employee>,onDismiss:()->Unit) {
    val names=people.associate { it.id to it.name }
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().fillMaxHeight(.78f)) {
            Column(Modifier.padding(14.dp)) {
                Text("سجل التعديلات",fontWeight=FontWeight.Bold,fontSize=18.sp)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    items(events,key={it.id}) { event ->
                        val from=Attendance.fromStored(event.before).label
                        val to=Attendance.fromStored(event.after).label
                        Column { Text("${names[event.employeeId] ?: "فرد محذوف"} • ${event.day}",fontWeight=FontWeight.Medium,fontSize=13.sp)
                            Text("$from ← $to     ${event.changed}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        HorizontalDivider()
                    }
                    if(events.isEmpty()) item { Text("لا توجد تعديلات بعد") }
                }
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)) {Text("إغلاق")}
            }
        }
    }
}
