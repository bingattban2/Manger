package com.manger.app.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.min
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun MonthlyScreen(
    data: MonthData, onExcel: (MonthData, String?) -> Unit, onPdf: (MonthData, String?) -> Unit,
    archived: Boolean = false, lockedSite: String? = null, allowExport: Boolean = true,
    rosterColumns: List<RosterColumn> = RosterColumnLayout.defaults
) {
    val columns = remember(rosterColumns) { RosterColumnLayout.sanitize(rosterColumns).filter { it.visible } }
    val month = data.month
    val availableSites = remember(data) { MonthSiteFilter.sites(data) }
    val siteCounts = remember(data) { data.employees.groupingBy { it.site.trim() }.eachCount() }
    val employeeNameSuggestions = remember(data) { data.employees.map { it.name }.distinct() }
    var selectedSite by remember(month, archived) { mutableStateOf<String?>(null) }
    var siteMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(availableSites, selectedSite) {
        if (selectedSite != null && selectedSite !in availableSites) selectedSite = null
    }
    val effectiveSite = lockedSite ?: selectedSite?.takeIf { it in availableSites }
    var search by remember(month, lockedSite) { mutableStateOf("") }
    val siteData = remember(data, effectiveSite) { MonthSiteFilter.select(data, effectiveSite) }
    val filtered = remember(siteData, search) {
        if (search.isBlank()) siteData
        else siteData.copy(employees = siteData.employees.filter { it.matchesSearch(search) })
    }
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    val density = LocalDensity.current
    val minimumToolsPx = 0f
    var toolsHeightPx by remember { mutableIntStateOf(0) }
    var hiddenToolsPx by remember { mutableFloatStateOf(0f) }
    val collapseConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val capacity = (toolsHeightPx - minimumToolsPx - hiddenToolsPx).coerceAtLeast(0f)
                val used = min(-available.y, capacity)
                hiddenToolsPx += used
                return Offset(0f, -used)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || hiddenToolsPx <= 0f) return Offset.Zero
                val used = min(available.y, hiddenToolsPx)
                hiddenToolsPx -= used
                return Offset(0f, used)
            }
        }
    }
    // SERVER: Apply the centrally authorized column order before rendering the table.
    val nameColumn = columns.first { it.key == "name" }
    val columnsBeforeName = remember(columns) { columns.takeWhile { it.key != "name" } }
    val pinThresholdPx = with(density) {
        columnsBeforeName.sumOf { rosterWidth(it.key).toPx().toDouble() }.toFloat()
    }
    val pinnedName by remember(horizontal, pinThresholdPx) {
        derivedStateOf { horizontal.value > pinThresholdPx }
    }
    val dayWidth = 42.dp
    val header = MaterialTheme.colorScheme.surfaceVariant
    val today = LocalDate.now()
    val activeDay = if (month == YearMonth.from(today)) today.dayOfMonth else -1
    val days = remember(month) { (1..month.lengthOfMonth()).map(month::atDay) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val fixedWidthPx = remember(columns, density) {
        with(density) { columns.sumOf { rosterWidth(it.key).toPx().toDouble() }.toFloat() }
    }
    val dayWidthPx = with(density) { dayWidth.toPx() }
    val viewportPx = with(density) { (screenWidthDp.dp - 8.dp).coerceAtLeast(100.dp).toPx() }
    val dayWindow by remember(days, horizontal, fixedWidthPx, dayWidthPx, viewportPx) {
        derivedStateOf { visibleDayWindow(horizontal.value, fixedWidthPx, dayWidthPx, viewportPx, days.size) }
    }
    Column(Modifier.fillMaxSize().nestedScroll(collapseConnection)) {
        // SERVER: Schema revisions must be authorized, versioned, and applied before rendering the roster.
        Box(Modifier.fillMaxWidth().height(with(density) { (toolsHeightPx - hiddenToolsPx).coerceAtLeast(1f).toDp() }).clipToBounds()) {
          Column(
              Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).onSizeChanged { newSize ->
                  if (newSize.height != toolsHeightPx) {
                      toolsHeightPx = newSize.height
                      hiddenToolsPx = hiddenToolsPx.coerceAtMost(newSize.height.toFloat())
                  }
              }, verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    (if (archived) "كشف مؤرشف • " else "أسماء القوة • ") + month.format(monthFormat),
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (lockedSite != null) {
            Text("موقع الحساب: $lockedSite", modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        if (!archived && lockedSite == null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("فرز الكشف حسب الموقع", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { siteMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(MonthSiteFilter.label(effectiveSite), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(5.dp))
                        Text("▾")
                    }
                    DropdownMenu(expanded = siteMenuExpanded, onDismissRequest = { siteMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("جميع المواقع (${data.employees.size})") }, onClick = {
                            selectedSite = null; siteMenuExpanded = false
                        })
                        availableSites.forEach { site ->
                            val count = siteCounts[site] ?: 0
                            DropdownMenuItem(
                                text = { Text("${MonthSiteFilter.label(site)} ($count)") },
                                onClick = { selectedSite = site; siteMenuExpanded = false }
                            )
                        }
                    }
                }
            }
            Text(
                "${filtered.employees.size} من ${data.employees.size} فرد • التصدير بحسب الفلتر",
                modifier = Modifier.padding(horizontal = 12.dp), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        KnownRecordField(
            value = search, onValueChange = { search = it },
            title = "البحث في الكشف بالاسم أو الرقم (ع)",
            options = employeeNameSuggestions,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        )
        if (allowExport) Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RoundedAction("تصدير Excel", { onExcel(filtered, if (archived) null else effectiveSite) }, Modifier.weight(1f))
            RoundedAction("تصدير PDF", { onPdf(filtered, if (archived) null else effectiveSite) }, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Attendance.entries.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(9.dp).background(colorFor(type), RoundedCornerShape(3.dp)))
                    Text("${type.symbol} ${type.label}", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
          }
        }
        // SERVER: Sync a complete schema revision, never overwrite pending local administrator edits.
        Box(Modifier.weight(1f).fillMaxWidth()) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LazyColumn(
            state = vertical,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp)
        ) {
            stickyHeader {
                MonthHeaderRow(
                    columns = columns, nameColumn = nameColumn, horizontal = horizontal,
                    pinnedName = pinnedName, days = days, dayWindow = dayWindow,
                    dayWidth = dayWidth, activeDay = activeDay, headerColor = header
                )
            }
            itemsIndexed(filtered.employees, key = { _, e -> e.id }, contentType = { _, _ -> "employee" }) { index, e ->
                MonthEmployeeRow(
                    employee = e, rowNumber = index + 1, data = filtered, columns = columns,
                    horizontal = horizontal, pinnedName = pinnedName, days = days,
                    dayWindow = dayWindow, today = today, activeDay = activeDay,
                    dayWidth = dayWidth
                )
            }
            if (filtered.employees.isEmpty()) {
                item { Text("لا توجد أسماء مطابقة للبحث أو الموقع المختار", Modifier.padding(25.dp)) }
            }
            }
          }
        }
    }
}

@Composable
private fun MonthHeaderRow(
    columns: List<RosterColumn>, nameColumn: RosterColumn,
    horizontal: ScrollState, pinnedName: Boolean,
    days: List<LocalDate>, dayWindow: VisibleDayWindow,
    dayWidth: androidx.compose.ui.unit.Dp, activeDay: Int, headerColor: Color
) {
    Surface(shadowElevation = 4.dp, color = headerColor) {
        Box(Modifier.fillMaxWidth().height(58.dp).clipToBounds()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(horizontal)) {
                columns.forEach { column ->
                    ReadableMonthCell(column.title, rosterWidth(column.key), headerColor,
                        bold = true, header = true, name = column.key == "name")
                }
                if (dayWindow.preceding > 0) Spacer(Modifier.width(dayWidth * dayWindow.preceding))
                days.subList(dayWindow.first, dayWindow.until).forEach { day ->
                    val background = if (day.dayOfMonth == activeDay)
                        MaterialTheme.colorScheme.tertiaryContainer else headerColor
                    DayHeaderCell(day, dayWidth, background)
                }
                if (dayWindow.remaining(days.size) > 0)
                    Spacer(Modifier.width(dayWidth * dayWindow.remaining(days.size)))
            }
            if (pinnedName) {
                Box(Modifier.align(Alignment.CenterStart).zIndex(1f).fillMaxHeight().clipToBounds()) {
                    ReadableMonthCell(nameColumn.title, rosterWidth("name"), headerColor,
                        bold = true, header = true, name = true)
                }
            }
        }
    }
}

@Composable
private fun MonthEmployeeRow(
    employee: Employee, rowNumber: Int, data: MonthData,
    columns: List<RosterColumn>, horizontal: ScrollState,
    pinnedName: Boolean, days: List<LocalDate>, dayWindow: VisibleDayWindow,
    today: LocalDate, activeDay: Int, dayWidth: androidx.compose.ui.unit.Dp
) {
    val colors = MaterialTheme.colorScheme
    val background = if (rowNumber % 2 == 1) colors.surface
        else colors.surfaceVariant.copy(alpha = .38f).compositeOver(colors.surface)
    Box(Modifier.fillMaxWidth().height(58.dp).background(background).clipToBounds()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(horizontal)) {
            columns.forEach { column ->
                val value = RosterColumnLayout.value(column, employee, rowNumber, data.columnValues)
                ReadableMonthCell(value, rosterWidth(column.key), background,
                    bold = column.key == "roster" || column.key == "name",
                    name = column.key == "name")
            }
            if (dayWindow.preceding > 0) Spacer(Modifier.width(dayWidth * dayWindow.preceding))
            days.subList(dayWindow.first, dayWindow.until).forEach { day ->
                val status = data.status(employee, day, today)
                val dayBackground = when {
                    status != null -> tintFor(status).compositeOver(background)
                    day.dayOfMonth == activeDay -> colors.tertiaryContainer
                    else -> background
                }
                ReadableMonthCell(status?.symbol.orEmpty(), dayWidth, dayBackground,
                    color = if (status == null) colors.onSurface else colorFor(status),
                    bold = status != null, dayStatus = true)
            }
            if (dayWindow.remaining(days.size) > 0)
                Spacer(Modifier.width(dayWidth * dayWindow.remaining(days.size)))
        }
        if (pinnedName) {
            Box(Modifier.align(Alignment.CenterStart).zIndex(1f).fillMaxHeight().clipToBounds()
                .background(background)) {
                ReadableMonthCell(employee.name, rosterWidth("name"), background,
                    bold = true, name = true)
            }
        }
    }
}

private fun rosterWidth(key: String) = when (key) {
    "ordinal" -> 48.dp
    "roster" -> 88.dp
    "name" -> 164.dp
    "site" -> 108.dp
    else -> 110.dp
}

@Composable private fun ReadableMonthCell(
    value: String,
    width: androidx.compose.ui.unit.Dp,
    background: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
    header: Boolean = false,
    name: Boolean = false,
    dayStatus: Boolean = false
) {
    Box(
        Modifier.width(width).fillMaxHeight().background(background)
            .border(.35.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = if (dayStatus) 0.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(value, color = color,
            fontSize = when {
                dayStatus -> 16.sp
                name -> 14.sp
                header -> 13.sp
                value.length > 14 -> 12.sp
                else -> 13.sp
            },
            lineHeight = 17.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = if (name || header) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
    }
}

@Composable private fun DayHeaderCell(
    day: LocalDate, width: androidx.compose.ui.unit.Dp, background: androidx.compose.ui.graphics.Color
) {
    Column(
        Modifier.width(width).height(58.dp).background(background)
            .border(.3.dp, MaterialTheme.colorScheme.outlineVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(day.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(daySymbol(day.dayOfWeek), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

internal fun daySymbol(day: DayOfWeek): String = when (day) {
    DayOfWeek.SUNDAY -> "أ"
    DayOfWeek.MONDAY -> "ن"
    DayOfWeek.TUESDAY -> "ث"
    DayOfWeek.WEDNESDAY -> "ر"
    DayOfWeek.THURSDAY -> "خ"
    DayOfWeek.FRIDAY -> "ج"
    DayOfWeek.SATURDAY -> "س"
}
