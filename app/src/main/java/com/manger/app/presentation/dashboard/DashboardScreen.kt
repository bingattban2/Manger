package com.manger.app.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manger.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

  
@Composable internal fun DashboardScreen(
    people: List<Employee>, today: LocalDate, marks: Map<Pair<Long, LocalDate>, Attendance>,
    siteScope: String? = null, knownSites: List<String> = emptyList(),
    unreadCount: Int = 0, onNotifications: () -> Unit = {},
    onSync: () -> Unit = {}, pendingSyncCount: Int = 0
) {
    val overview = remember(people, marks, today) { DailyOverviewBuilder.build(people, marks, today) }
    val admin = siteScope == null
    var chosenSite by remember(siteScope) { mutableStateOf<String?>(null) }
    var chosenStatus by remember { mutableStateOf<String?>(null) }
    var siteExpanded by remember { mutableStateOf(false) }
    val registeredEmptySites = remember(overview, knownSites) {
        knownSites.filter { name -> overview.sites.none { it.title == name } }
            .map { name -> DailySite("registered:$name", name, emptyList(), DayStats(0, 0, 0, 0, 0)) }
    }
    val allSites = (overview.sites + registeredEmptySites).sortedBy { it.title }
    val matchingSite = allSites.firstOrNull { it.key == chosenSite }
    val roster = if (admin && matchingSite != null) matchingSite.employees else overview.employees
    val stats = if (admin && matchingSite != null) matchingSite.stats else overview.stats
    val filtered = roster.filter { chosenStatus == null || it.status.name == chosenStatus }
    val dayLabel = today.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("ar")))
    val dateLabel = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 98.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                IconButton(onClick = onNotifications, modifier = Modifier.size(46.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔔", fontSize = 21.sp)
                        if (unreadCount > 0) Text("${unreadCount.coerceAtMost(99)}${if (unreadCount > 99) "+" else ""}",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (admin) "التمام اليومي الكلي للقوة" else "التمام اليومي لموقع ${siteScope.orEmpty()}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 3)
                Column(horizontalAlignment = Alignment.End) {
                    Text(dayLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(dateLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "sync") {
            OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                Text(if (admin) "تحديث الإدارة مع السيرفر" else "تحديث بيانات موقعي من السيرفر")
            }
            if (pendingSyncCount > 0) Text("تعديلات محلية بانتظار المزامنة: $pendingSyncCount",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        item(key = "strength") {
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("القوة الكلية بحسب الكشف", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("${stats.total}", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, fontSize = 27.sp)
                }
            }
        }
        item(key = "status-counts") {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Attendance.entries.chunked(3).forEach { chunk ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        chunk.forEach { status ->
                            Card(onClick = { chosenStatus = if (chosenStatus == status.name) null else status.name },
                                modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(
                                    containerColor = colorFor(status).copy(alpha = if (chosenStatus == status.name) .24f else .11f))) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${stats.count(status)}", color = colorFor(status),
                                        fontWeight = FontWeight.Bold, fontSize = 25.sp)
                                    Text(status.label, color = colorFor(status), fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                        repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (admin) item(key = "site-picker") {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("فرز التحضير بحسب الموقع", fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { siteExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text((matchingSite?.title ?: "الكل — جميع المواقع") + "  ▾", maxLines = 1)
                    }
                    DropdownMenu(expanded = siteExpanded, onDismissRequest = { siteExpanded = false }) {
                        DropdownMenuItem(text = { Text("الكل — جميع المواقع") }, onClick = {
                            chosenSite = null; chosenStatus = null; siteExpanded = false
                        })
                        allSites.forEach { site ->
                            DropdownMenuItem(text = { Text("${site.title} (${site.stats.total})") }, onClick = {
                                chosenSite = site.key; chosenStatus = null; siteExpanded = false
                            })
                        }
                    }
                }
            }
        }
        item(key = "state-filter") {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (admin) "الفلتر — ${matchingSite?.title ?: "الكل"}" else "فلتر الحالات",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = chosenStatus == null, onClick = { chosenStatus = null },
                            label = { Text("الكل") })
                        Attendance.entries.forEach { status ->
                            FilterChip(selected = chosenStatus == status.name,
                                onClick = { chosenStatus = if (chosenStatus == status.name) null else status.name },
                                label = { Text(status.label, color = colorFor(status), fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tintFor(status)))
                        }
                    }
                    Text("المعروض ${filtered.size} من ${stats.total} فرد",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (filtered.isNotEmpty()) {
            if (admin) {
                item(key = "table-header") {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
                        TableHead("الرقم (ع)", Modifier.weight(.8f))
                        TableHead("الاسم", Modifier.weight(2.1f))
                        TableHead("الموقع", Modifier.weight(1.25f))
                        TableHead("الحالة", Modifier.weight(1.05f))
                    }
                }
                items(filtered, key = { "daily-${it.employee.id}" }) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        TableValue(row.employee.rosterNumber.ifBlank { "—" }, Modifier.weight(.8f))
                        TableValue(row.employee.name, Modifier.weight(2.1f))
                        TableValue(row.employee.site.ifBlank { "غير محدد" }, Modifier.weight(1.25f))
                        TableValue(row.status.label, Modifier.weight(1.05f), color = colorFor(row.status))
                    }
                }
            } else {
                item(key = "roster-header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("الاسم", Modifier.weight(1.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("الموقع", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("الحالة", Modifier.width(65.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                items(filtered, key = { "daily-${it.employee.id}" }) { row ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                        RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(row.employee.name, Modifier.weight(1.7f), fontSize = 13.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(row.employee.site.ifBlank { "غير محدد" }, Modifier.weight(1f),
                            fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(row.status.label, Modifier.width(65.dp), color = colorFor(row.status),
                            fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                    }
                }
            }
        } else item(key = "empty") {
            Text("لا توجد أسماء مطابقة لهذا الفلتر", Modifier.fillMaxWidth().padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable private fun TableHead(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.height(44.dp).border(.5.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(horizontal = 4.dp, vertical = 11.dp),
        fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
}

@Composable private fun TableValue(text: String, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, modifier.height(56.dp).border(.5.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(horizontal = 4.dp, vertical = 10.dp),
        fontSize = 11.sp, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
