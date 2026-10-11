package com.manger.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manger.app.domain.RosterColumnLayout
import com.manger.app.domain.RosterColumn
import com.manger.app.domain.LocalSiteInfo

@Composable internal fun RosterColumnsEditor(initial: List<RosterColumn>, onSave: (List<RosterColumn>) -> Unit) {
    val columns = remember(initial) { mutableStateListOf<RosterColumn>().also { it.addAll(initial) } }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("التعديل على هيكل الكشف", style = MaterialTheme.typography.titleMedium)
        Text("غيّر أسماء الأعمدة الثابتة وترتيبها أو أضف عموداً جديداً. عمود الاسم إلزامي، وأعمدة أيام الشهر لن تتغير.",
            style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(columns.size, key={columns[it].key}) { index ->
                val column = columns[index]
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked=column.visible, enabled=column.key!="name", onCheckedChange={
                                columns[index]=column.copy(visible=it); error=null
                            })
                            OutlinedTextField(column.title, onValueChange={
                                columns[index]=column.copy(title=it.take(42)); error=null
                            }, label={Text("اسم العمود")}, singleLine=true, modifier=Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
                            TextButton(enabled=index>0, onClick={columns.add(index-1,columns.removeAt(index))}) { Text("↑ للأعلى") }
                            TextButton(enabled=index<columns.lastIndex, onClick={columns.add(index+1,columns.removeAt(index))}) { Text("↓ للأسفل") }
                        }
                    }
                }
            }
        }
        if (error != null) Text(error.orEmpty(), color=MaterialTheme.colorScheme.error)
        OutlinedButton(onClick={
            if(columns.size>=20) error="الحد الأقصى 20 عموداً"
            else { columns.add(RosterColumnLayout.create()); error=null }
        }, modifier=Modifier.fillMaxWidth()) { Text("＋ إضافة عمود جديد") }
        Button(onClick={
            runCatching { RosterColumnLayout.validate(columns.toList()) }.onSuccess { confirm=true }
                .onFailure { error=it.localizedMessage ?: "تحقق من أسماء الأعمدة" }
        }, modifier=Modifier.fillMaxWidth()) { Text("حفظ التعديلات") }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("تأكيد تعديل الكشوفات")},
        text={Text("سيُطبّق ترتيب الأعمدة وأسماؤها على جميع الكشوفات، دون تعديل أعمدة أيام الشهر أو حالات التحضير. هل تريد الحفظ؟")},
        confirmButton={TextButton(onClick={
            confirm=false; onSave(RosterColumnLayout.validate(columns.toList()))
        }){ Text("تأكيد الحفظ") }},dismissButton={TextButton(onClick={confirm=false}){Text("إلغاء")}})
}

@Composable internal fun SiteRosterDeleteScreen(
    sites: List<LocalSiteInfo>, onDelete: (Set<String>) -> Unit, saving: Boolean
) {
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("تعديل الكشوفات — حذف كشوف المواقع", style = MaterialTheme.typography.titleMedium)
        Text("حدد المواقع المطلوب حذف أفرادها. الأرشيف القديم لا يتغير، ولا يُسمح بحذف موقع مرتبط بحساب مشرف.",
            style = MaterialTheme.typography.bodySmall)
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f, fill=false).heightIn(max=420.dp)) {
            items(sites,key={ it.name }) { site ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = checked[site.name] == true, onCheckedChange = { checked[site.name] = it })
                Text("${site.name} — ${site.totalEmployees} فرد", Modifier.weight(1f))
            }
            }
        }
        Button(onClick = { confirm = true }, enabled = !saving && checked.any { it.value }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف الكشوف المحددة") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm=false }, title = { Text("تأكيد حذف كشوف المواقع") },
        text = { Text("سيتم حذف جميع أفراد ${checked.count { it.value }} موقع وسجلات تحضيرهم الجارية من هذا الهاتف. لن يمكن التراجع إلا من نسخة احتياطية. هل تريد المتابعة؟") },
        confirmButton = { TextButton(onClick = {
            confirm=false
            onDelete(checked.filterValues { it }.keys.toSet())
            checked.clear()
        }) { Text("حذف نهائياً") } },
        dismissButton = { TextButton(onClick = { confirm=false }) { Text("تراجع") } })
}
