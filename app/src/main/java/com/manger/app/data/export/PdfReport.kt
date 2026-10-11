package com.manger.app.data.export

import com.manger.app.domain.*

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate

  
object PdfReport {
    private const val WIDTH=1191
    private const val HEIGHT=842
    private const val LEFT=30f
    private const val RIGHT=1161f
    private const val ROW=25f
    private const val TABLE_TOP=110f
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun text(canvas: android.graphics.Canvas, value: String, x: Float, y: Float, size: Float=11f, color: Int=Color.BLACK, bold:Boolean=false, right:Boolean=false) {
        paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size;paint.typeface=if(bold) Typeface.create("sans-serif",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL)
        paint.textAlign=if(right) Paint.Align.RIGHT else Paint.Align.CENTER
        canvas.drawText(value,x,y,paint)
    }
    private fun rect(canvas: android.graphics.Canvas, l:Float,t:Float,r:Float,b:Float,color:Int) {
        paint.style=Paint.Style.FILL;paint.color=color;canvas.drawRect(l,t,r,b,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.5f;paint.color=Color.LTGRAY;canvas.drawRect(l,t,r,b,paint);paint.style=Paint.Style.FILL
    }
    fun write(month: MonthData, destination:OutputStream,
              rosterColumns: List<RosterColumn> = RosterColumnLayout.defaults) {
        val columns=RosterColumnLayout.sanitize(rosterColumns).filter { it.visible }
        val pdf=PdfDocument();val today=LocalDate.now();val days=month.month.lengthOfMonth()
        val fixedWidth= minOf(560f,(RIGHT-LEFT)*.5f)
        val dayWidth=(RIGHT-LEFT-fixedWidth)/days
        val cellWidth=fixedWidth/columns.size
        val pageSize=26
        val pages=maxOf(1,(month.employees.size+pageSize-1)/pageSize)
        for(p in 0 until pages) {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,p+1).create());val c=page.canvas
            c.drawColor(Color.WHITE)
            text(c,"تقرير التحضير الشهري – ${month.month}",RIGHT,49f,22f,Color.rgb(29,59,94),true,true)
            val legend = Attendance.entries.map { "${it.symbol} ${it.label}" }
            legend.chunked(8).take(4).forEachIndexed { i, line ->
                text(c, "الرموز: " + line.joinToString("  •  "), LEFT + 410f,
                    61f + i * 12f, 9f, Color.DKGRAY)
            }
            text(c,"الصفحة ${p+1} / $pages",LEFT+120f,49f,11f,Color.DKGRAY,false,true)
            val daysLeft=LEFT+fixedWidth
            columns.forEachIndexed { index, col ->
                val l=LEFT+index*cellWidth
                rect(c,l,TABLE_TOP,l+cellWidth,TABLE_TOP+ROW,Color.rgb(221,232,244))
                val label=col.title.let { if(it.length>16) it.take(15)+"…" else it }
                text(c,label,l+cellWidth/2,TABLE_TOP+17f,if(columns.size>9) 7f else 9f,Color.BLACK,true)
            }
            for(day in 1..days) {
                val x=daysLeft+(day-1)*dayWidth
                rect(c,x,TABLE_TOP,x+dayWidth,TABLE_TOP+ROW,Color.rgb(221,232,244))
                text(c,day.toString(),x+dayWidth/2,TABLE_TOP+17f,10f,Color.BLACK,true)
            }
            val slice=month.employees.drop(p*pageSize).take(pageSize)
            slice.forEachIndexed { index,e ->
                val y=TABLE_TOP+ROW*(index+1)
                columns.forEachIndexed { colIndex, col ->
                    val x=LEFT+colIndex*cellWidth
                    rect(c,x,y,x+cellWidth,y+ROW,if(index%2==0) Color.WHITE else Color.rgb(248,249,251))
                    val value=RosterColumnLayout.value(col,e,p*pageSize+index+1,month.columnValues)
                    val limit=when { columns.size>12 -> 6; columns.size>7 -> 11; else -> 22 }
                    text(c,if(value.length>limit) value.take(limit-1)+"…" else value,x+cellWidth/2,y+17f,
                        if(columns.size>9) 7f else 9f)
                }
                for(day in 1..days) {
                    val x=daysLeft+(day-1)*dayWidth
                    val status=month.status(e,month.month.atDay(day),today)
                    val color = if (status == null) Color.WHITE else {
                        val base = status.argb
                        fun mix(v: Int) = (((base shr v) and 255L) * .23 + 255 * .77).toInt()
                        Color.rgb(mix(16), mix(8), mix(0))
                    }
                    rect(c,x,y,x+dayWidth,y+ROW,color)
                    if(status!=null) text(c,status.symbol,x+dayWidth/2,y+17f,12f,Color.BLACK,true)
                }
            }
            text(c,"تم الإنشاء بواسطة تطبيق إدارة التمام اليومي",WIDTH/2f,HEIGHT-30f,10f,Color.GRAY)
            pdf.finishPage(page)
        }
        try { pdf.writeTo(destination) } finally { pdf.close() }
    }
}
