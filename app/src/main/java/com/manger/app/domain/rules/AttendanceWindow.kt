package com.manger.app.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter

  
data class AttendanceWindow(
    val enabled: Boolean = false,
    val from: LocalTime = LocalTime.of(6, 0),
    val to: LocalTime = LocalTime.of(18, 0)
) {
    init { require(from != to) { "يجب أن يختلف وقت البداية عن وقت النهاية" } }

      
    fun allows(time: LocalTime): Boolean = !enabled || when {
        from.isBefore(to) -> !time.isBefore(from) && time.isBefore(to)
        else -> !time.isBefore(from) || time.isBefore(to)
    }

    fun description(): String = if (!enabled) "التحضير متاح طوال اليوم" else
        "التحضير متاح من ${display(from)} إلى ${display(to)}" +
            if (from.isAfter(to)) " (يمتد إلى اليوم التالي)" else ""

    fun serialize(): String = "${if (enabled) 1 else 0}|${display(from)}|${display(to)}"

    companion object {
        private val format = DateTimeFormatter.ofPattern("HH:mm")
        fun display(time: LocalTime): String = time.format(format)
        fun parse(raw: String?): AttendanceWindow = runCatching {
            val parts = requireNotNull(raw).split('|')
            require(parts.size == 3 && parts[0] in listOf("0", "1"))
            AttendanceWindow(parts[0] == "1", LocalTime.parse(parts[1], format), LocalTime.parse(parts[2], format))
        }.getOrElse { AttendanceWindow() }
    }
}

class AttendanceWindowClosedException(message: String) : IllegalStateException(message)
