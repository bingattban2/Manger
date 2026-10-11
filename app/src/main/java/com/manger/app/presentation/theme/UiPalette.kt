package com.manger.app.presentation

import androidx.compose.ui.graphics.Color
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.manger.app.domain.Attendance

internal val green=Color(0xFF21885A)
internal val red=Color(0xFFCA4350)
internal val blue=Color(0xFF3477CB)
internal val gold=Color(0xFFBA8317)
internal val primary=Color(0xFF164A74)
private val arabicLocale=Locale.forLanguageTag("ar")
internal val dateFormat=DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy",arabicLocale)
internal val monthFormat=DateTimeFormatter.ofPattern("MMMM yyyy",arabicLocale)

internal fun colorFor(status: Attendance?): Color = if (status == null) Color.Gray else Color(status.argb.toInt())
internal fun tintFor(status: Attendance?): Color = colorFor(status).copy(alpha = 0.12f)
