package com.manger.app.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.manger.app.data.export.RosterBatchExporter
import com.manger.app.data.export.ExportProgressNotifier
import com.manger.app.presentation.monthly.ExportRosterDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.manger.app.domain.*
import com.manger.app.application.AppContainer
import com.manger.app.domain.repository.DashboardSnapshot
import com.manger.app.data.export.PdfReport
import com.manger.app.data.export.Spreadsheet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Throwable.messageArabic() = localizedMessage ?: "حدث خطأ غير متوقع"

@Composable
internal fun MangerApp(container: AppContainer, resumeRevision:Int, session: LocalSession,
    onExitApp: () -> Unit, onLogout: () -> Unit) {
    val db = container.database
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val prefs=remember { context.getSharedPreferences("manger_settings",0) }
    val rosterImporter = container.rosterImport
    val dashboardRepository = container.dashboard
    var rosterColumns by remember { mutableStateOf(db.rosterColumnLayout()) }
    var attendanceWindow by remember { mutableStateOf(db.attendanceWindow()) }
    var clockTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(resumeRevision, session.username) {
        attendanceWindow = withContext(Dispatchers.IO) { db.attendanceWindow() }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            clockTime = LocalTime.now()
            delay(15_000L)
        }
    }

    
    LaunchedEffect(session.username) {
        val old = prefs.getString("roster_columns", null)
        if (old != null && !old.startsWith("v2|") && rosterColumns == RosterColumnLayout.defaults && session.isAdmin) {
            runCatching { db.saveRosterColumnLayout(RosterColumnLayout.decode(old)) }
                .onSuccess { rosterColumns = db.rosterColumnLayout(); prefs.edit().remove("roster_columns").apply() }
        }
    }
    var clearDeviceConfirm by remember { mutableStateOf(false) }
    var deleteConfirmationText by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark",false)) }
    val colors=if(dark) darkColorScheme(primary=Color(0xFF9BC9FF), secondary=Color(0xFF91CFAC)) else lightColorScheme(primary=primary,secondary=green)
    var page by remember { mutableIntStateOf(0) }
    val previousPages = remember { mutableStateListOf<Int>() }
    var notificationsOpen by remember { mutableStateOf(false) }
    var messageComposeOpen by remember { mutableStateOf(false) }
    var messageBackConfirm by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }
    var revokedDuty by remember { mutableStateOf<String?>(null) }
    var accountsOpen by remember { mutableStateOf(false) }
    var accountsReturnPage by remember { mutableIntStateOf(4) }
    
    var managementMode by remember { mutableIntStateOf(0) }
    val employeeDrafts = remember { mutableStateMapOf<Long, Employee>() }
    var savingEmployees by remember { mutableStateOf(false) }
    var deletingRosters by remember { mutableStateOf(false) }
    
    var pendingManagementExit by remember { mutableStateOf<Int?>(null) }
    var calendarRevision by remember { mutableIntStateOf(0) }
    var selectedArchive by remember { mutableStateOf<YearMonth?>(null) }
    var exportData by remember { mutableStateOf<MonthData?>(null) }
    var exportFormat by remember { mutableStateOf<RosterBatchExporter.Format?>(null) }
    var exportSnapshot by remember { mutableStateOf<MonthData?>(null) }
    var selectedExportTree by remember {
        mutableStateOf(prefs.getString("export_folder_tree",null)?.let(Uri::parse))
    }
    var exportBusy by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }
    var exportFraction by remember { mutableFloatStateOf(0f) }
    var awaitingNotificationChoice by remember { mutableStateOf<RosterBatchExporter.Choice?>(null) }
    var scheduledExport by remember { mutableStateOf<RosterBatchExporter.Choice?>(null) }
    var periodExport by remember { mutableStateOf<PeriodReport?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) { rosterColumns = withContext(Dispatchers.IO) { db.rosterColumnLayout() } }
    var archiveVersion by remember { mutableIntStateOf(0) }
    var attendanceDialog by remember { mutableStateOf(false) }
    var attendanceSaving by remember { mutableStateOf(false) }
    var attendanceError by remember { mutableStateOf<String?>(null) }
    var newEmployee by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var restoreAutoConfirm by remember { mutableStateOf(false) }
    var auditOpen by remember { mutableStateOf(false) }
    var reportEmployee by remember { mutableStateOf<Employee?>(null) }
    var statusEditing by remember { mutableStateOf<Attendance?>(null) }
    var statusCreate by remember { mutableStateOf(false) }
    var pendingSheets by remember { mutableStateOf<Map<String, List<ImportedEmployee>>?>(null) }
    val selectedImportSheets = remember { mutableStateMapOf<String, Boolean>() }
    var importCheck by remember { mutableStateOf<Result<ImportCheck>?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    var importDone by remember { mutableFloatStateOf(0f) }
    val chosenImportRecords = remember(pendingSheets, selectedImportSheets.toMap()) {
        pendingSheets.orEmpty().filterKeys { selectedImportSheets[it] == true }.values.flatten()
    }
    LaunchedEffect(chosenImportRecords, version) {
        importCheck = null
        if (chosenImportRecords.isNotEmpty() && !importBusy) {
            importCheck = runCatching { rosterImporter.preview(chosenImportRecords) }
        }
    }
    var importError by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) { val message=toastMessage; if(message!=null) { snackbar.showSnackbar(message);toastMessage=null } }
    fun notify(message:String) {
        version++; toastMessage=message
        com.manger.app.data.sync.HourlySyncScheduler.triggerAfterLocalChange(context)
    }
    fun fail(e:Throwable) { toastMessage="تعذرت العملية: ${e.messageArabic()}" }
    val dashboard by produceState(
        initialValue = DashboardSnapshot(), version, resumeRevision, session, notificationsOpen
    ) {
        value = dashboardRepository.load(session, notificationsOpen)
    }
    val people = dashboard.employees
    val employeeNames = remember(people) { people.map { it.name }.distinct() }
    val siteDetails = dashboard.siteDetails
    val knownSites = dashboard.knownSites
    val unreadInbox = dashboard.unreadInbox
    val pendingSyncCount = dashboard.pendingSyncCount
    val inboxItems = dashboard.inboxItems
    val today=remember(resumeRevision,calendarRevision,version) { LocalDate.now() }
    val currentMonth = YearMonth.from(today)
    val monthly by produceState(initialValue = MonthData(currentMonth, emptyList(), emptyMap()), currentMonth, version, session) {
        value = withContext(Dispatchers.IO) { db.month(currentMonth) }
    }
    val todayMarks by produceState(initialValue = emptyMap<Pair<Long, LocalDate>, Attendance>(), today, version, session) {
        value = withContext(Dispatchers.IO) { db.marksBetween(today,today) }
    }
    val archives by produceState(initialValue = emptyList<YearMonth>(), archiveVersion, calendarRevision) {
        value = withContext(Dispatchers.IO) { db.archivedMonths() }
    }
    val archiveData by produceState<MonthData?>(initialValue = null, selectedArchive, archiveVersion, session) {
        value = withContext(Dispatchers.IO) { selectedArchive?.let { db.archivedMonth(it) } }
    }

    LaunchedEffect(resumeRevision) {
        val result = withContext(Dispatchers.IO) { runCatching { db.archiveCompletedMonths() } }
        result.onSuccess { if (it > 0) { version++; archiveVersion++ } }
            .onFailure { fail(it) }
        calendarRevision++
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            val untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
                .toMillis().coerceAtLeast(1000L)
            delay(untilMidnight + 300L)
            calendarRevision++
            val result = withContext(Dispatchers.IO) { runCatching { db.archiveCompletedMonths() } }
            result.onSuccess { if (it > 0) { version++; archiveVersion++ } }
                .onFailure { fail(it) }
        }
    }

    var adminHourConsent by remember { mutableStateOf(false) }
    LaunchedEffect(session.isAdmin) {
        if (session.isAdmin) while (isActive) {
            delay(60_000L)
            if (com.manger.app.data.sync.SyncBackend.isConfigured &&
                System.currentTimeMillis() / 3_600_000L !=
                    prefs.getLong("sync_consent_hour",-1)) {
                prefs.edit().putLong("sync_consent_hour",System.currentTimeMillis()/3_600_000L).apply()
                adminHourConsent = true
            }
        }
    }
    
    LaunchedEffect(resumeRevision, calendarRevision, version) {
        if (!session.isAdmin) {
            val decision = runCatching { db.supervisorDuty(session) }.getOrNull()
            if (decision == null || !decision.allowed) {
                attendanceDialog = false
                revokedDuty = "تم سحب صلاحيات التعديل منك. ${decision?.message ?: "تعذر التحقق من الحضور"}"
            }
        }
    }

    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && !importBusy) com.manger.app.application.operations.AppOperationScope.scope.launch {
            importBusy = true
            importProgress = "جارٍ قراءة وتحليل ملف Excel..."
            importDone = 0f
            val result=withContext(Dispatchers.IO) { runCatching {
                val stream=context.contentResolver.openInputStream(uri) ?: error("تعذر فتح الملف")
                val fileName=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else "data.xlsx" } ?: "data.xlsx"
                rosterImporter.read(stream, fileName) { current, total ->
                    scope.launch { importProgress = "فحص أوراق الملف: $current / $total"; importDone = current.toFloat()/total.coerceAtLeast(1) }
                }
            } }
            importBusy = false
            result.onSuccess { sheets ->
                selectedImportSheets.clear()
                sheets.keys.forEach { selectedImportSheets[it] = true }
                pendingSheets = sheets
                importProgress = ""
            }.onFailure { error ->
                android.util.Log.e("MangerExcel", "Failed to import employee spreadsheet", error)
                importError = "${error.messageArabic()}\n\nتُقبل ملفات XLSX وCSV مع أعمدة الرقم (ع) والاسم والموقع."
                importProgress = ""
            }
        }
    }
    val exportDirectoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                selectedExportTree = uri
                prefs.edit().putString("export_folder_tree", uri.toString()).apply()
            }
            result.onFailure(::fail)
        }
    }
    val exportNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        
        scheduledExport = awaitingNotificationChoice
        awaitingNotificationChoice = null
    }
    LaunchedEffect(scheduledExport) {
        val choice = scheduledExport ?: return@LaunchedEffect
        scheduledExport = null
        val snapshot = exportSnapshot ?: return@LaunchedEffect
        val format = exportFormat ?: return@LaunchedEffect
        val tree = selectedExportTree
        exportBusy = true
        exportFraction = 0f
        exportProgress = "جارٍ تجهيز ملفات التصدير..."
        com.manger.app.application.operations.AppOperationScope.scope.launch {
            val notifier = ExportProgressNotifier(context.applicationContext)
            var completed = 0
            var total = (if (choice.fullRoster) 1 else 0) + choice.sites.size
            val result = withContext(Dispatchers.IO) { runCatching {
                RosterBatchExporter.export(context.applicationContext, snapshot, choice, format, tree, rosterColumns) { done, count, file ->
                    completed = done; total = count
                    notifier.show(done, count, file)
                    scope.launch {
                        exportFraction = done.toFloat() / count.coerceAtLeast(1)
                        exportProgress = "تم تصدير $done من $count • $file"
                    }
                }
            } }
            result.onSuccess { outcome ->
                notifier.finish("تم إنشاء ${outcome.files} ملف في ${outcome.folderLabel}", true)
                exportBusy = false
                exportFormat = null
                exportSnapshot = null
                toastMessage = "اكتمل تصدير ${outcome.files} كشف إلى ${outcome.folderLabel}"
            }.onFailure { error ->
                notifier.finish("اكتمل $completed من $total ملف. ${error.localizedMessage.orEmpty()}", false)
                exportBusy = false
                fail(IllegalStateException("توقف التصدير بعد $completed من $total ملف: ${error.messageArabic()}",error))
            }
        }
    }
    val xlsxLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if(uri!=null) {
            val report = exportData ?: monthly
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { Spreadsheet.write(report,it,rosterColumns) } ?: error("تعذر إنشاء الملف")
                } }
                result.onSuccess { toastMessage="تم حفظ تقرير Excel" }.onFailure(::fail)
                exportData = null
            }
        } else exportData = null
    }
    val periodXlsxLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val report = periodExport
        periodExport = null
        if (uri != null && report != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.use { Spreadsheet.writePeriodReport(report, it) }
                    ?: error("تعذر إنشاء ملف Excel")
            } }
            result.onSuccess { toastMessage = "تم تصدير تقرير Excel" }.onFailure(::fail)
        }
    }
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) {
            val report = exportData ?: monthly
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { PdfReport.write(report,it,rosterColumns) } ?: error("تعذر إنشاء الملف")
                } }
                result.onSuccess { toastMessage="تم حفظ التقرير PDF" }.onFailure(::fail)
                exportData = null
            }
        } else exportData = null
    }
    val backupLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(db.backup().toByteArray(Charsets.UTF_8)) }
                    ?: error("تعذر إنشاء النسخة")
            } }
            result.onSuccess { toastMessage="تم إنشاء النسخة الاحتياطية" }.onFailure(::fail)
        }
    }
    val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val content=context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("تعذر قراءة الملف")
                db.restore(content)
                db.archiveCompletedMonths()
            } }
            result.onSuccess { selectedArchive=null; archiveVersion++; notify("تمت استعادة البيانات بنجاح") }.onFailure(::fail)
        }
    }

    fun navigateTo(target: Int) {
        if (page != target) {
            previousPages.add(page)
            page = target
        }
    }
    fun closeManagement(target: Int) {
        employeeDrafts.clear()
        managementMode = 0
        if (target >= 0) navigateTo(target)
    }
    fun requestManagementExit(target: Int) {
        if (savingEmployees) return
        if (page == 3 && managementMode == 2 && employeeDrafts.isNotEmpty())
            pendingManagementExit = target
        else closeManagement(target)
    }
    fun saveEmployeeDrafts(targetAfterSave: Int? = null) {
        if (savingEmployees || employeeDrafts.isEmpty()) return
        val updates = employeeDrafts.values.toList()
        savingEmployees = true
        com.manger.app.application.operations.AppOperationScope.scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { db.updateEmployeesBatch(updates) } }
            savingEmployees = false
            result.onSuccess { count ->
                employeeDrafts.clear()
                notify("تم حفظ تعديلات $count فرد")
                if (targetAfterSave != null) closeManagement(targetAfterSave)
            }.onFailure(::fail)
        }
    }
    BackHandler(enabled = true) {
        when {
            messageBackConfirm || exitConfirm || pendingManagementExit != null -> Unit
            notificationsOpen -> notificationsOpen = false
            messageComposeOpen -> messageBackConfirm = true
            accountsOpen -> { accountsOpen = false; page = accountsReturnPage }
            page == 2 && selectedArchive != null -> selectedArchive = null
            page == 3 && managementMode != 0 && !savingEmployees -> {
                managementMode = when(managementMode) {
                    5, 6 -> 4
                    7, 8 -> 1
                    9, 10 -> 8
                    else -> { requestManagementExit(-2); managementMode }
                }
            }
            page != 0 -> {
                val last = if (previousPages.isNotEmpty()) previousPages.removeAt(previousPages.lastIndex) else 0
                if (page == 3) requestManagementExit(last) else page = last
            }
            else -> exitConfirm = true
        }
    }

    MaterialTheme(colorScheme=colors) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Scaffold(
                snackbarHost={SnackbarHost(snackbar)},
                floatingActionButton={ if (!notificationsOpen && !messageComposeOpen && !accountsOpen && page != 5) FloatingActionButton(
                    onClick = {
                        if (session.isAdmin || attendanceWindow.allows(LocalTime.now())) attendanceDialog = true
                        else notify("وضع القراءة فقط: ${attendanceWindow.description()}")
                    }, containerColor = if (session.isAdmin || attendanceWindow.allows(clockTime))
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(if (session.isAdmin || attendanceWindow.allows(clockTime)) "+ تحضير" else "التحضير مغلق",
                        color = if (session.isAdmin || attendanceWindow.allows(clockTime)) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold)
                } },
                bottomBar={ if (!notificationsOpen && !messageComposeOpen)
                    RoleNavigationBar(session.isAdmin, page, accountsOpen, savingEmployees) { target ->
                        accountsOpen = false
                        if (page == 3 && target != 3) requestManagementExit(target) else navigateTo(target)
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (notificationsOpen) NotificationInboxScreen(session.isAdmin, inboxItems,
                        onBack = { notificationsOpen = false })
                    else if (messageComposeOpen && session.isAdmin) NotificationComposeScreen(
                        sites = knownSites, onBack = { messageComposeOpen = false },
                        onSend = { text, site ->
                            val result = runCatching { db.sendSupervisorMessage(text,site) }
                            result.onSuccess { count ->
                                messageComposeOpen = false
                                notify("تم حفظ الرسالة لعدد $count من المشرفين على هذا الجهاز")
                            }.onFailure(::fail)
                            result.isSuccess
                        })
                    else if (accountsOpen && session.isAdmin) LocalAdminScreen(db, onBack = { accountsOpen = false; page = accountsReturnPage }, onChanged = {
                        version++
                        com.manger.app.data.sync.HourlySyncScheduler.triggerAfterLocalChange(context)
                    })
                    else when(page) {
                        0 -> DashboardScreen(people,today,todayMarks,siteScope=session.site, knownSites=knownSites,
                            unreadCount=unreadInbox,
                            pendingSyncCount=pendingSyncCount,
                            onSync={
                                syncStatus = com.manger.app.data.sync.SyncBackend.explain()
                            }, onNotifications={
                                runCatching { db.markInboxSeen() }.onSuccess { version++; notificationsOpen=true }
                                    .onFailure(::fail)
                            })
                        1 -> MonthlyScreen(monthly, rosterColumns=rosterColumns,
                            onExcel={ _, _ -> if(session.isAdmin) {
                                exportSnapshot=monthly; exportFormat=RosterBatchExporter.Format.XLSX
                            } },
                            onPdf={ _, _ -> if(session.isAdmin) {
                                exportSnapshot=monthly; exportFormat=RosterBatchExporter.Format.PDF
                            } }, lockedSite=session.site, allowExport=session.isAdmin)
                        2 -> if (!com.manger.app.data.sync.SyncBackend.isConfigured) {
                            Column(Modifier.fillMaxSize().padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("الأرشيف عبر السيرفر فقط", style = MaterialTheme.typography.titleLarge)
                                Text("لا تُعرض الأرشيفات المحفوظة محلياً. عند ربط Supabase سيتم جلب أرشيف الحساب من السيرفر بعد التحقق من الاتصال والصلاحيات.")
                                Text(com.manger.app.data.sync.SyncBackend.explain(),
                                    color = MaterialTheme.colorScheme.error)
                            }
                        } else ArchiveScreen(archives,selectedArchive,archiveData,
                            onSelect={selectedArchive=it},onBack={selectedArchive=null},
                            onExcel={if(session.isAdmin) archiveData?.let { report ->
                                exportData=report; xlsxLauncher.launch("manger_archive_${report.month}.xlsx")
                            }},
                            onPdf={if(session.isAdmin) archiveData?.let { report ->
                                exportData=report; pdfLauncher.launch("manger_archive_${report.month}.pdf")
                            }}, siteScope=session.site)
                        3 -> if (session.isAdmin) EmployeesScreen(
                            people, managementMode, employeeDrafts, savingEmployees,
                            onModeChange = { managementMode = it },
                            onExit = {
                                managementMode = when(managementMode) {
                                    5, 6 -> 4
                                    7, 8 -> 1
                                    9, 10 -> 8
                                    else -> { requestManagementExit(-2); managementMode }
                                }
                            },
                            onNew = { newEmployee = true },
                            onImport = { importLauncher.launch(arrayOf("*/*")) },
                            onStage = { changed ->
                                val stored = people.firstOrNull { it.id == changed.id }
                                if (stored != null) {
                                    if (stored == changed) employeeDrafts.remove(changed.id)
                                    else employeeDrafts[changed.id] = changed
                                }
                            },
                            onSave = { saveEmployeeDrafts() },
                            onReport = { reportEmployee = it },
                            statuses = Attendance.entries,
                            onStatusAdd = { statusCreate = true },
                            onStatusEdit = { statusEditing = it },
                            onDelete = { employee ->
                                runCatching { db.deleteEmployee(employee.id) }
                                    .onSuccess { employeeDrafts.remove(employee.id); notify("تم حذف الفرد وسجلات تحضيره الجارية") }
                                    .onFailure(::fail)
                            },
                            siteDetails = siteDetails,
                            onSiteAdd = { name -> runCatching { db.addSite(name) }
                                .onSuccess { notify("تم حفظ الموقع الجديد") }.onFailure(::fail).isSuccess },
                            onSiteRename = { old, updated -> runCatching { db.renameSite(old,updated) }
                                .onSuccess { notify("تم حفظ اسم الموقع وتحديث ارتباطاته") }.onFailure(::fail).isSuccess },
                            onSiteDelete = { name -> runCatching { db.removeSite(name) }
                                .onSuccess { notify("تم حذف الموقع") }.onFailure(::fail).isSuccess },
                            onSupervisorManage = { accountsReturnPage = 3; accountsOpen = true },
                            onMessageCompose = { messageComposeOpen = true },
                            onReportPage = { navigateTo(5) },
                            onDeleteSiteRosters = { sites ->
                                if (!deletingRosters) {
                                    deletingRosters = true
                                    com.manger.app.application.operations.AppOperationScope.scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching { db.deleteEntireSiteRosters(sites) }
                                        }
                                        deletingRosters = false
                                        result.onSuccess { notify("تم حذف $it فرد من المواقع المحددة، وحُفظت العمليات في طابور المزامنة") }
                                            .onFailure(::fail)
                                    }
                                }
                            },
                            deletingRosters = deletingRosters,
                            rosterColumns = rosterColumns,
                            onColumnsSave = { columns ->
                                runCatching { db.saveRosterColumnLayout(columns) }
                                    .onSuccess { rosterColumns = db.rosterColumnLayout(); notify("تم حفظ هيكل الكشف لجميع المواقع") }
                                    .onFailure(::fail)
                            },
                            customColumnValues = monthly.columnValues,
                            onCustomValueSave = { id, columnKey, value ->
                                runCatching { db.saveCustomValue(id,columnKey,value) }
                                    .onSuccess { notify("تم تحديث بيانات العمود الإضافي") }
                                    .onFailure(::fail)
                            }
                        )
                        5 -> PeriodReportsScreen(
                            people = people,
                            isAdmin = session.isAdmin,
                            loadReport = { ids, from, to -> db.periodReport(ids, from, to) },
                            onExcel = { report ->
                                periodExport = report
                                periodXlsxLauncher.launch("manger_report_${report.start}_${report.end}.xlsx")
                            },
                            onExit = {
                                page = if (previousPages.isNotEmpty())
                                    previousPages.removeAt(previousPages.lastIndex)
                                else if (session.isAdmin) 3 else 0
                            }
                        )
                        4 -> SettingsScreen(session, dark,
                            onDark={dark=it;prefs.edit().putBoolean("dark",it).apply()},
                            onBackup={if(session.isAdmin) backupLauncher.launch("manger_backup_${LocalDate.now()}.json")},
                            onRestore={if(session.isAdmin) restoreConfirm=true},
                            onRestoreAuto={if(session.isAdmin) restoreAutoConfirm=true},
                            onAudit={if(session.isAdmin) auditOpen=true},
                            onLocalManage={if(session.isAdmin){ accountsReturnPage=4;accountsOpen=true }},
                            onLogout=onLogout,
                            onClearDeviceData={ clearDeviceConfirm=true },
                            autoBackupInfo=prefs.getString("auto_backup_last", null)?.let { "آخر نسخة تلقائية: $it" } ?: "لم تُحفظ نسخة تلقائية بعد",
                            automaticBackups=remember(resumeRevision,version) {
                                java.io.File(context.filesDir, "daily_backups").listFiles()
                                    ?.count { it.extension == "json" } ?: 0
                            },
                            versionName=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "غير محدد",
                            attendanceWindow=attendanceWindow,
                            onAttendanceWindowSave={ window ->
                                runCatching { db.saveAttendanceWindow(window) }
                                    .onSuccess { attendanceWindow = window; notify("تم اعتماد وقت التحضير") }
                                    .onFailure(::fail)
                            })
                    }
                }
            }
            if (revokedDuty != null && !session.isAdmin) AlertDialog(
                onDismissRequest = {},
                title = { Text("تم إيقاف صلاحية المشرف") },
                text = { Text(revokedDuty.orEmpty()) },
                confirmButton = { TextButton(onClick = { revokedDuty = null; onLogout() }) { Text("العودة لتسجيل الدخول") } }
            )
            if (exitConfirm) AlertDialog(
                onDismissRequest = { exitConfirm = false }, title = { Text("تأكيد الخروج من التطبيق") },
                text = { Text("هل تريد إغلاق التطبيق؟") },
                confirmButton = { TextButton(onClick = { exitConfirm = false; onExitApp() }) { Text("خروج") } },
                dismissButton = { TextButton(onClick = { exitConfirm = false }) { Text("إلغاء") } }
            )
            if (messageBackConfirm) AlertDialog(
                onDismissRequest = { messageBackConfirm = false }, title = { Text("إلغاء كتابة الإشعار؟") },
                text = { Text("سيتم الرجوع دون إرسال الرسالة. هل تريد المتابعة؟") },
                confirmButton = { TextButton(onClick = { messageBackConfirm = false; messageComposeOpen = false }) { Text("تأكيد الإلغاء") } },
                dismissButton = { TextButton(onClick = { messageBackConfirm = false }) { Text("متابعة الكتابة") } }
            )
            if (pendingManagementExit != null) {
                val target = pendingManagementExit!!
                AlertDialog(
                    onDismissRequest = { pendingManagementExit = null },
                    title = { Text("تغييرات غير محفوظة") },
                    text = { Text("هناك ${employeeDrafts.size} فرد تم تعديل بياناته ولم تُحفظ. هل تريد حفظ التعديلات قبل الخروج؟") },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingManagementExit = null
                            saveEmployeeDrafts(target)
                        }) { Text("حفظ والخروج") }
                    },
                    dismissButton = {
                        Column {
                            TextButton(onClick = {
                                pendingManagementExit = null
                                closeManagement(target)
                            }) { Text("الخروج دون حفظ", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { pendingManagementExit = null }) { Text("متابعة التعديل") }
                        }
                    }
                )
            }
            if (attendanceDialog && !session.isAdmin && !attendanceWindow.allows(clockTime) && !attendanceSaving) {
                LaunchedEffect(clockTime, attendanceWindow) { attendanceDialog = false; attendanceError = null }
            }
            if(attendanceDialog && (session.isAdmin || attendanceWindow.allows(clockTime))) AttendanceEditor(
                people = people, saving = attendanceSaving, errorMessage = attendanceError, isAdmin = session.isAdmin,
                onDismiss = { if (!attendanceSaving) { attendanceDialog = false; attendanceError = null } },
                onSave = { groups ->
                    if (!attendanceSaving) {
                        attendanceSaving = true
                        attendanceError = null
                        com.manger.app.application.operations.AppOperationScope.scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { db.applyAttendanceBatch(groups) }
                            }
                            attendanceSaving = false
                            result.onSuccess {
                                attendanceDialog = false
                                notify("تم اعتماد ${groups.size} مجموعة وتحديث $it حالة بنجاح")
                            }.onFailure {
                                if (it is SupervisorDutyDeniedException) {
                                    attendanceDialog = false
                                    revokedDuty = it.messageArabic()
                                } else attendanceError = it.messageArabic()
                            }
                        }
                    }
                }
            )
            if(newEmployee) EmployeeEditor(null,onDismiss={newEmployee=false},defaultCode={db.nextCode()},
                availableSites=knownSites,knownEmployeeNames=employeeNames,onSubmit={_,name,code,start,site,rosterNumber ->
                runCatching { db.addEmployee(name,code,start,site,rosterNumber) }.onSuccess{newEmployee=false;notify("تمت إضافة الفرد")}.onFailure(::fail)
            })
            if (exportFormat != null && session.isAdmin) ExportRosterDialog(
                format = exportFormat!!,
                sites = (knownSites + monthly.employees.map { it.site.trim() })
                    .filter { it.isNotBlank() }.distinct().sorted(),
                folder = RosterBatchExporter.locationLabel(context,selectedExportTree),
                busy = exportBusy, progress = exportProgress, progressFraction = exportFraction,
                onPickFolder = { exportDirectoryLauncher.launch(null) },
                onDefaultFolder = {
                    selectedExportTree = null
                    prefs.edit().remove("export_folder_tree").apply()
                },
                onCancel = { if (!exportBusy) { exportFormat = null; exportSnapshot = null } },
                onConfirm = { choice ->
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        awaitingNotificationChoice = choice
                        exportNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else scheduledExport = choice
                }
            )
            if(importError!=null) AlertDialog(onDismissRequest={importError=null},
                title={Text("تعذر استيراد ملف الافراد")},
                text={Text(importError.orEmpty())},
                confirmButton={TextButton(onClick={importError=null}){Text("حسناً")}})
            if(pendingSheets != null) {
                ImportPreviewDialog(
                    sheets = pendingSheets!!,
                    selected = selectedImportSheets.filterValues { it }.keys,
                    onToggle = { name -> selectedImportSheets[name] = selectedImportSheets[name] != true },
                    check = importCheck, saving = importBusy,
                    progressText = importProgress, progress = importDone,
                    onImport = {
                        if (!importBusy && importCheck?.isSuccess == true) {
                            val records = chosenImportRecords.toList()
                            importBusy = true
                            importProgress = "جارٍ حفظ الكشف في قاعدة البيانات..."
                            importDone = 0f
                            com.manger.app.application.operations.AppOperationScope.scope.launch {
                                val result=withContext(Dispatchers.IO) { runCatching {
                                    rosterImporter.commit(records) { count, total ->
                                        scope.launch { importProgress = "حفظ $count من $total فرد"; importDone = count.toFloat()/total.coerceAtLeast(1) }
                                    }
                                } }
                                importBusy = false
                                importProgress = ""
                                result.onSuccess {
                                    pendingSheets = null; selectedImportSheets.clear()
                                    notify("تمت إضافة ${it.added} وتحديث ${it.updated} فرد (رقم ع)")
                                }.onFailure { error ->
                                    android.util.Log.e("MangerExcel", "Employee import database failure", error)
                                    importError = "تعذر حفظ الكشف، ولم يُعتمد أي جزء منه: ${error.messageArabic()}"
                                }
                            }
                        }
                    },
                    onDismiss = { if (!importBusy) { pendingSheets = null; selectedImportSheets.clear() } }
                )
            }
            if (importBusy && pendingSheets == null) AlertDialog(
                onDismissRequest = {},
                title = { Text("جاري معالجة الكشف") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(importProgress)
                    LinearProgressIndicator(progress = { importDone }, modifier = Modifier.fillMaxWidth())
                } },
                confirmButton = {}
            )
            if(reportEmployee!=null) EmployeeHistoryDialog(reportEmployee!!, version, loadHistory = { id -> db.employeeHistory(id) }, onDismiss={reportEmployee=null})
            if (statusCreate || statusEditing != null) StatusEditorDialog(
                original = statusEditing,
                onDismiss = { statusCreate = false; statusEditing = null },
                onSave = { name, symbol, argb ->
                    runCatching { db.saveStatus(statusEditing?.name, name, symbol, argb) }
                        .onSuccess {
                            statusCreate = false; statusEditing = null
                            notify("تم اعتماد تعريف الحالة وتطبيقه على جميع الشاشات")
                        }.onFailure(::fail)
                }
            )
            if(auditOpen) AuditDialog(db.audit(),people,onDismiss={auditOpen=false})
            if (adminHourConsent && session.isAdmin) AlertDialog(
                onDismissRequest={adminHourConsent=false},
                title={Text("حان وقت تحديث بيانات الإدارة")},
                text={Text("هل تريد متابعة تحديث السيرفر الآن أم تأجيله؟")},
                confirmButton={TextButton(onClick={
                    adminHourConsent=false
                    com.manger.app.data.sync.HourlySyncScheduler.triggerAfterLocalChange(context)
                }){Text("التحديث الآن")}},
                dismissButton={TextButton(onClick={adminHourConsent=false}){Text("لاحقاً")}}
            )
            if (syncStatus != null) AlertDialog(
                onDismissRequest = { syncStatus=null }, title = { Text("تحديث بيانات السيرفر") },
                text = { Text(syncStatus.orEmpty()) },
                confirmButton = { TextButton(onClick = { syncStatus=null }) { Text("حسناً") } }
            )
            if (clearDeviceConfirm && session.isAdmin) AlertDialog(
                onDismissRequest = { clearDeviceConfirm=false },
                title = { Text("حذف جميع البيانات من الهاتف؟") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم حذف جميع البيانات المحلية من هذا الهاتف فقط، ولن يتأثر السيرفر. يوجد $pendingSyncCount تغييراً معلّقاً؛ سيفقد الحذف هذه العمليات إن لم ترفع إلى السيرفر. احتفظ بنسخة احتياطية.")
                    Text("للتأكيد النهائي اكتب كلمة حذف:")
                    OutlinedTextField(deleteConfirmationText, { deleteConfirmationText=it },
                        singleLine=true, modifier=Modifier.fillMaxWidth())
                } },
                confirmButton = { TextButton(enabled=deleteConfirmationText.trim()=="حذف",onClick = {
                    clearDeviceConfirm=false
                    runCatching {
                        db.close()
                        check(context.deleteDatabase("manger_attendance.db")) { "تعذر حذف قاعدة البيانات" }
                        java.io.File(context.filesDir,"daily_backups").deleteRecursively()
                        context.getSharedPreferences("manger_settings",0).edit().clear().commit()
                        context.getSharedPreferences("manger_lock",0).edit().clear().commit()
                    }.onSuccess {
                        android.widget.Toast.makeText(context,"تم حذف بيانات الهاتف. أعد تشغيل التطبيق.",android.widget.Toast.LENGTH_LONG).show()
                        onExitApp()
                    }.onFailure(::fail)
                }) { Text("تأكيد الحذف") } },
                dismissButton = { TextButton(onClick = { clearDeviceConfirm=false }) { Text("تراجع") } }
            )
            if(restoreAutoConfirm && session.isAdmin) AlertDialog(
                onDismissRequest={restoreAutoConfirm=false},
                title={Text("استعادة آخر نسخة تلقائية")},
                text={Text("سيتم استبدال بيانات التطبيق والحسابات بأحدث نسخة تلقائية محفوظة على الجهاز. سيُطلب منك تسجيل الدخول مرة أخرى بعد النجاح. هل تريد المتابعة؟")},
                confirmButton={TextButton(onClick={
                    restoreAutoConfirm=false
                    scope.launch {
                        val result=withContext(Dispatchers.IO) { runCatching {
                            val directory=java.io.File(context.filesDir,"daily_backups")
                            val latest=directory.listFiles()?.filter { it.extension=="json" }
                                ?.maxByOrNull { it.name } ?: error("لا توجد نسخة تلقائية محفوظة")
                            db.restore(latest.readText(Charsets.UTF_8))
                            db.archiveCompletedMonths()
                        } }
                        result.onSuccess {
                            android.widget.Toast.makeText(context,"تمت الاستعادة. سجّل دخولك من جديد.",android.widget.Toast.LENGTH_LONG).show()
                            onLogout()
                        }.onFailure(::fail)
                    }
                }) { Text("تأكيد الاستعادة") }},
                dismissButton={TextButton(onClick={restoreAutoConfirm=false}){Text("إلغاء")}}
            )
            if(restoreConfirm) AlertDialog(onDismissRequest={restoreConfirm=false},title={Text("تأكيد الاستعادة")},text={Text("ستُستبدل جميع البيانات الحالية ببيانات ملف النسخة الاحتياطية. يُفضّل حفظ نسخة أولاً.")},confirmButton={TextButton(onClick={restoreConfirm=false;restoreLauncher.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text("متابعة")}},dismissButton={TextButton(onClick={restoreConfirm=false}){Text("إلغاء")}})

        }
    }
}

