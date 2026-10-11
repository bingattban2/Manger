package com.manger.app.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.Employee

@Composable
internal fun EmployeeRosterSearchBar(query:String,onSearch:(String)->Unit,sortBySite:Boolean,
    filterSite:String?, sites:List<String>,employeeNames:List<String>,onSort:(Boolean)->Unit,onSite:(String?)->Unit) {
    var opened by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        KnownRecordField(query,onSearch,modifier=Modifier.weight(1f),options=employeeNames,
            title="بحث بالاسم أو الرقم (ع)")
        Box {
            OutlinedButton(onClick={opened=true}) {Text("فرز ▾",fontSize=12.sp)}
            DropdownMenu(expanded=opened,onDismissRequest={opened=false}) {
                DropdownMenuItem(text={Text("الترتيب الأبجدي")},onClick={onSort(false);onSite(null);opened=false})
                DropdownMenuItem(text={Text("الترتيب حسب الموقع")},onClick={onSort(true);onSite(null);opened=false})
                DropdownMenuItem(text={Text("جميع المواقع")},onClick={onSite(null);opened=false})
                sites.forEach { name ->
                    DropdownMenuItem(text={Text(if (name.isBlank()) "بدون موقع" else name)},onClick={onSite(name);opened=false})
                }
            }
        }
    }
}

@Composable
internal fun EmployeeRosterRow(employee:Employee,index:Int,changed:Boolean,saving:Boolean,
    hasCustom:Boolean,onEdit:()->Unit,onCustom:()->Unit,onReport:()->Unit,onDelete:()->Unit,onArchive:()->Unit) {
    ElevatedCard(modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("${(index+1).toString().padStart(3,'0')}  ${employee.name}",
                    fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Text(if(changed)"غير محفوظ" else if(employee.active)"نشط" else "مؤرشف",
                    fontSize=11.sp,color=if(changed) gold else if(employee.active) green else red)
            }
            Text("الرقم (ع): ${employee.rosterNumber.ifBlank { "غير مسجل" }}  •  الرقم الداخلي: ${employee.code}  •  الموقع: ${employee.site.ifBlank { "بدون موقع" }}  •  الالتحاق: ${employee.started}",
                fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick=onEdit,enabled=!saving) {Text("تعديل")}
                if(hasCustom) TextButton(onClick=onCustom) {Text("أعمدة إضافية")}
                TextButton(onClick=onReport) {Text("تقرير")}
                TextButton(onClick=onDelete,enabled=!saving) {Text("حذف",color=MaterialTheme.colorScheme.error)}
                TextButton(onClick=onArchive,enabled=!saving) {Text(if(employee.active)"أرشفة" else "إعادة تنشيط")}
            }
        }
    }
}
