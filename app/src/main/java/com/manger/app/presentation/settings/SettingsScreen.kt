package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.manger.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.LocalSession
import com.manger.app.domain.AttendanceWindow
import java.time.LocalTime
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext

@Composable internal fun SettingsScreen(
    session: LocalSession, dark: Boolean, onDark: (Boolean) -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit, onRestoreAuto: () -> Unit,
    onAudit: () -> Unit, onLocalManage: () -> Unit, onLogout: () -> Unit,
    onClearDeviceData: () -> Unit,
    autoBackupInfo: String, automaticBackups: Int, versionName: String,
    attendanceWindow: AttendanceWindow, onAttendanceWindowSave: (AttendanceWindow) -> Unit
) {
    var logoutConfirm by remember { mutableStateOf(false) }
    var extraExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الإعدادات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("بيانات الحساب", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                SettingValue("نوع الحساب", if (session.isAdmin) "إدارة عامة" else "مشرف")
                SettingValue("اسم المستخدم", session.username)
                if (!session.isAdmin) SettingValue("الموقع", session.site.orEmpty())
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الوضع الليلي", modifier = Modifier.weight(1f))
                    Switch(checked = dark, onCheckedChange = onDark)
                }
            }
        }
        if (session.isAdmin) {
            AttendanceWindowSettings(attendanceWindow, onAttendanceWindowSave)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("النسخة الاحتياطية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("نسخة عامة للإدارة تشمل المواقع والمشرفين والافراد والتحضير والأرشيف.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("تصدير نسخة احتياطية") }
                    OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("استيراد نسخة احتياطية") }
                    HorizontalDivider()
                    Text("نسخة تلقائية يومياً حوالي الساعة 10:00 مساءً، في التخزين الخاص بالتطبيق.", fontSize = 12.sp)
                    Text(autoBackupInfo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (automaticBackups > 0) OutlinedButton(onClick = onRestoreAuto, modifier = Modifier.fillMaxWidth()) {
                        Text("استعادة آخر نسخة تلقائية ($automaticBackups محفوظة)")
                    }
                    Text("قد يؤخر نظام Android مهمة الخلفية، وتُستكمل عند فتح التطبيق بعد العاشرة. النسخ الداخلية تُحذف عند إزالة التطبيق؛ صدّر نسخة خارجية للاحتفاظ بها.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Text("حذف جميع بيانات التطبيق من هذا الهاتف فقط", fontWeight = FontWeight.Bold)
                    Text("لا يحذف أي بيانات من السيرفر. إذا كانت هناك تغييرات لم تُرسل فستُفقد نهائياً عند تأكيد الحذف. احتفظ بنسخة احتياطية أولاً.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearDeviceData) { Text("حذف البيانات المحلية", color = MaterialTheme.colorScheme.error) }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { extraExpanded = !extraExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (extraExpanded) "إخفاء أدوات الإدارة ▲" else "أدوات الإدارة والمراجعة ▼")
                    }
                    if (extraExpanded) {
                        OutlinedButton(onClick = onLocalManage, modifier = Modifier.fillMaxWidth()) { Text("إدارة المشرفين") }
                        OutlinedButton(onClick = onAudit, modifier = Modifier.fillMaxWidth()) { Text("سجل التعديلات") }
                    }
                }
            }
        }
        if (!session.isAdmin) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("وقت التحضير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(attendanceWindow.description(), style = MaterialTheme.typography.bodyMedium)
                    Text("خارج هذه الفترة يكون التحضير للقراءة فقط، وتبقى باقي الخيارات متاحة.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("حماية البيانات: حذف نسخة المشرف المحلية كل 24 ساعة لا يبدأ قبل تفعيل استعادة آمنة من السيرفر والتأكد من رفع جميع العمليات المعلّقة. لن تُحذف الكشوف دون نسخة مؤكدة على الخادم.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حول التطبيق", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                SettingValue("اسم التطبيق", stringResource(R.string.app_name))
                SettingValue("رقم الإصدار", versionName)
                HorizontalDivider()
                Text("فكرة وبرمجة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("سعيد بن قطبان", fontWeight = FontWeight.Bold)
            }
        }
        Button(onClick = { logoutConfirm = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("تسجيل الخروج")
        }
        Spacer(Modifier.height(74.dp))
    }
    if (logoutConfirm) AlertDialog(onDismissRequest = { logoutConfirm = false },
        title = { Text("تسجيل الخروج") }, text = { Text("هل تريد تسجيل الخروج من هذا الحساب؟") },
        confirmButton = { TextButton(onClick = { logoutConfirm = false; onLogout() }) { Text("تسجيل الخروج") } },
        dismissButton = { TextButton(onClick = { logoutConfirm = false }) { Text("إلغاء") } })
}

@Composable private fun SettingValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.4f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

  
@Composable private fun AttendanceWindowSettings(
    current: AttendanceWindow,
    onSave: (AttendanceWindow) -> Unit
) {
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var from by remember(current) { mutableStateOf(current.from) }
    var to by remember(current) { mutableStateOf(current.to) }
    var confirm by remember { mutableStateOf(false) }
    val changed = enabled != current.enabled || from != current.from || to != current.to
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("وقت التحضير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("تُحدد الإدارة ساعات السماح للمشرفين بتعديل التحضير. خارج الفترة تبقى الكشوف والتقارير والعمليات الأخرى متاحة للقراءة والاستخدام.",
                style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تقييد وقت التحضير", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AttendanceTimeButton("من", from, Modifier.weight(1f)) { from = it }
                AttendanceTimeButton("إلى", to, Modifier.weight(1f)) { to = it }
            }
            if (from == to) Text("يجب أن يختلف وقت البداية عن وقت النهاية", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            Text(if (enabled && from != to) AttendanceWindow(true, from, to).description()
                 else if (!enabled) "التقييد متوقف: يمكن للمشرف التحضير في أي وقت" else "اختر وقتين مختلفين",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(),
                enabled = changed && from != to) { Text("حفظ وقت التحضير") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("اعتماد وقت التحضير") },
        text = { Text("هل تريد تطبيق ساعات التحضير الجديدة على جميع حسابات المشرفين في هذا الجهاز؟") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            onSave(AttendanceWindow(enabled, from, to))
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}

@Composable private fun AttendanceTimeButton(label: String, time: LocalTime, modifier: Modifier, onChoose: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        TimePickerDialog(context, { _, hour, minute -> onChoose(LocalTime.of(hour, minute)) },
            time.hour, time.minute, true).show()
    }, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(AttendanceWindow.display(time), fontWeight = FontWeight.Bold)
        }
    }
}
