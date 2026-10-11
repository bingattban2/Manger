package com.manger.app.presentation

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable internal fun RoundedAction(title:String, onClick:()->Unit, modifier:Modifier=Modifier) {
    OutlinedButton(onClick=onClick,modifier=modifier) { Text(title,maxLines=1,fontSize=12.sp) }
}


@Composable internal fun Cell(value:String,width: androidx.compose.ui.unit.Dp,bg:Color,color:Color=MaterialTheme.colorScheme.onSurface,bold:Boolean=false) {
    Box(Modifier.width(width).height(48.dp).background(bg).border(.3.dp,MaterialTheme.colorScheme.outlineVariant),contentAlignment=Alignment.Center) {
        Text(value,fontSize=if(value.length>10) 11.sp else 12.sp,color=color,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}


@Composable internal fun DateButton(label:String, date:LocalDate?, onDate:(LocalDate)->Unit, onClear:()->Unit) {
    val context=LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=8.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable {
                val selected=date ?: LocalDate.now()
                DatePickerDialog(context,{_,year,month,day->onDate(LocalDate.of(year,month+1,day))},selected.year,selected.monthValue-1,selected.dayOfMonth).show()
            }.padding(8.dp)) {
                Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date?.toString() ?: "اليوم افتراضياً",fontSize=13.sp)
            }
            if(date!=null) TextButton(onClick=onClear) {Text("مسح")}
        }
    }
}

