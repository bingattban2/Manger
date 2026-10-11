package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.LocalInboxEvent

  
@Composable internal fun NotificationInboxScreen(
    isAdmin: Boolean, events: List<LocalInboxEvent>, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("→ رجوع") }
            Text(if (isAdmin) "إشعارات تغييرات المشرفين" else "رسائل الإدارة", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
        }
        if (events.isEmpty()) {
            Text(if (isAdmin) "لا توجد تغييرات مسجلة من المشرفين على هذا الجهاز"
                else "لم تصلك رسائل محفوظة على هذا الجهاز", modifier = Modifier.padding(18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 95.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(events, key = { it.id }) { event ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(event.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (isAdmin) {
                                Text("المشرف: ${event.actorName} | المستخدم: ${event.actorUsername}", fontSize = 12.sp)
                                Text("الموقع: ${event.site}", fontSize = 12.sp)
                            } else {
                                Text("المرسل: ${event.actorName}", fontSize = 12.sp)
                            }
                            Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                            Text(event.createdAt, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

  
@Composable internal fun NotificationComposeScreen(
    sites: List<String>, onBack: () -> Unit, onSend: (String, String?) -> Boolean
) {
    var selectedSite by remember { mutableStateOf<String?>(null) }
    var showSites by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmSend by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("إرسال إشعار للمشرفين", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("المرسل", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("البشرية", fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("المستلم", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Box(Modifier.weight(2f)) {
                        OutlinedButton(onClick = { showSites = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedSite ?: "الكل — جميع المشرفين", maxLines = 1)
                        }
                        DropdownMenu(expanded = showSites, onDismissRequest = { showSites = false }) {
                            DropdownMenuItem(text = { Text("الكل — جميع المشرفين") }, onClick = {
                                selectedSite = null; showSites = false
                            })
                            sites.forEach { site ->
                                DropdownMenuItem(text = { Text(site) }, onClick = { selectedSite = site; showSites = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = message, onValueChange = { if (it.length <= 2500) message = it },
                    label = { Text("نص الرسالة") }, placeholder = { Text("اكتب الإشعار الذي سيظهر للمشرفين") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), minLines = 6,
                    supportingText = { Text("${message.length}/2500") }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(onClick = { confirmSend = true }, enabled = message.isNotBlank(),
                modifier = Modifier.weight(1f)) { Text("إرسال") }
        }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("تأكيد إلغاء الرسالة") },
        text = { Text("هل تريد إلغاء كتابة الرسالة والرجوع دون إرسال؟") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onBack() }) { Text("نعم، إلغاء") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("متابعة الكتابة") } })
    if (confirmSend) AlertDialog(onDismissRequest = { confirmSend = false },
        title = { Text("تأكيد إرسال الإشعار") },
        text = { Text("سيتم حفظ الرسالة للمشرفين المرتبطين بـ ${selectedSite ?: "جميع المواقع"}. هل تريد المتابعة؟") },
        confirmButton = { TextButton(onClick = {
            confirmSend = false
            onSend(message.trim(), selectedSite)
        }) { Text("تأكيد الإرسال") } },
        dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("رجوع") } })
}
