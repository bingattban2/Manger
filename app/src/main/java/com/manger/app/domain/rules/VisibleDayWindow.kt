package com.manger.app.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class VisibleDayWindow(val first: Int, val until: Int) {
    val preceding: Int get() = first
    fun remaining(total: Int): Int = (total - until).coerceAtLeast(0)
}

fun visibleDayWindow(scrollPx: Int, fixedColumnsPx: Float, dayPx: Float, viewportPx: Float, count: Int): VisibleDayWindow {
    if (count <= 0 || dayPx <= 0f) return VisibleDayWindow(0, 0)
    val first = max(0, floor(((scrollPx - fixedColumnsPx).coerceAtLeast(0f) / dayPx).toDouble()).toInt() - 2)
    val visible = max(1, ceil((viewportPx.coerceAtLeast(dayPx) / dayPx).toDouble()).toInt()) + 6
    return VisibleDayWindow(min(count, first), min(count, first + visible))
}
