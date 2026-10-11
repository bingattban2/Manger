package com.manger.app.data.local

import com.manger.app.domain.*

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AttendanceDb(context: Context) : SQLiteOpenHelper(context, "manger_attendance.db", null, 14) {
    @Volatile private var activeSession: LocalSession? = null

      
    fun activate(session: LocalSession?) { activeSession = session }
    private fun signedIn(): LocalSession = activeSession ?: error("يلزم تسجيل الدخول أولاً")
    private fun adminOnly() {
        check(AccessPolicy.has(signedIn().role, AppPermission.MANAGE_ALL_ROSTERS)) {
            "هذه العملية مخصصة للإدارة العامة"
        }
    }

    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE employees (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, site TEXT NOT NULL DEFAULT '', started TEXT NOT NULL, ended TEXT, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE marks (employee_id INTEGER NOT NULL, day TEXT NOT NULL, status TEXT NOT NULL, changed TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(employee_id,day), FOREIGN KEY(employee_id) REFERENCES employees(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX idx_marks_day ON marks(day)")
        db.execSQL("CREATE TABLE audit (id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL, day TEXT NOT NULL, before_state TEXT NOT NULL, after_state TEXT NOT NULL, changed TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        createArchiveTables(db)
        createStatuses(db)
        setArchiveStart(db, YearMonth.now())
        createLocalAccounts(db)
        createSiteAliases(db)
        createInboxTable(db)
        createDutyLink(db)
        createRosterIndexes(db)
        createSyncOutbox(db)
        createCustomColumns(db)
        SiteIdentityDao.migrate(db)
        SyncStateDao.createSchema(db)
        ensureTemporaryAdmin(db, resetExisting = false)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        
        if (oldVersion < 2) db.execSQL("ALTER TABLE employees ADD COLUMN site TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 3) {
            createArchiveTables(db)
            
            
            val initial = inferLegacyStart(db, YearMonth.now())
            setArchiveStart(db, initial)
        }
        if (oldVersion < 4) {
            
            db.execSQL("CREATE TABLE marks_v4 (employee_id INTEGER NOT NULL, day TEXT NOT NULL, status TEXT NOT NULL, changed TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(employee_id,day), FOREIGN KEY(employee_id) REFERENCES employees(id) ON DELETE CASCADE)")
            db.execSQL("INSERT INTO marks_v4(employee_id,day,status,changed) SELECT employee_id,day,status,changed FROM marks")
            db.execSQL("DROP TABLE marks")
            db.execSQL("ALTER TABLE marks_v4 RENAME TO marks")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_marks_day ON marks(day)")
            createStatuses(db)
        }
        if (oldVersion < 5) createLocalAccounts(db)
        if (oldVersion < 6) createSiteAliases(db)
        if (oldVersion < 7) createInboxTable(db)
        if (oldVersion < 8) createDutyLink(db)
        if (oldVersion < 9) createRosterIndexes(db)
        if (oldVersion < 10) createSyncOutbox(db)
        if (oldVersion < 11) createCustomColumns(db)
        if (oldVersion < 12) { SiteIdentityDao.migrate(db); SyncStateDao.createSchema(db) }
        if (oldVersion < 13) removeLegacyDemoCredentials(db)
        if (oldVersion < 14) ensureTemporaryAdmin(db, resetExisting = true)
    }

    private fun createCustomColumns(db: SQLiteDatabase) = RosterColumnDao.createSchema(db)

      
    fun attendanceWindow(): AttendanceWindow {
        signedIn()
        val stored = readableDatabase.rawQuery(
            "SELECT value FROM app_meta WHERE name='attendance_window'", null
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        return AttendanceWindow.parse(stored)
    }

    fun saveAttendanceWindow(value: AttendanceWindow) {
        adminOnly()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR REPLACE INTO app_meta(name,value) VALUES('attendance_window',?)",
                arrayOf(value.serialize()))
            recordChange(db, "attendance_window", "UPSERT", "attendance_window", null,
                JSONObject().put("enabled", value.enabled)
                    .put("from", AttendanceWindow.display(value.from))
                    .put("to", AttendanceWindow.display(value.to)))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // SERVER: Enforce edit window, exact daily supervisor presence, target day and scope again on PostgreSQL.
    private fun requireAttendanceTime(session: LocalSession) {
        if (session.isAdmin) return
        val window = attendanceWindow()
        if (!window.allows(java.time.LocalTime.now())) throw AttendanceWindowClosedException(
            "انتهى وقت التحضير المحدد من الإدارة. ${window.description()}. يمكنك الاطلاع على البيانات وتنفيذ العمليات الأخرى.")
    }

    fun rosterColumnLayout(): List<RosterColumn> {
        signedIn()
        return RosterColumnDao.loadLayout(readableDatabase)
    }

    fun saveRosterColumnLayout(columns: List<RosterColumn>) {
        adminOnly()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val serialized = RosterColumnDao.saveLayout(db, columns)
            recordChange(db,"roster_layout","UPSERT","roster_layout",null,JSONObject().put("columns",serialized))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // SERVER: Apply only an authenticated, authorized schema snapshot; never overwrite pending admin edits.
    fun applyServerColumnDefinitions(rows: JSONArray, cursor: Long): Boolean {
        signedIn()
        require(cursor > 0L) { "رقم تحديث السيرفر غير صالح" }
        val definitions = (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            item.getInt("position") to RosterColumn(
                item.getString("column_key"), item.getString("title"),
                item.optBoolean("visible", true)
            )
        }.sortedBy { it.first }.map { it.second }
        val validated = RosterColumnLayout.validate(definitions)
        val db = writableDatabase
        val hasPending = db.rawQuery(
            "SELECT 1 FROM sync_outbox WHERE entity='roster_layout' LIMIT 1", null
        ).use { it.moveToFirst() }
        if (hasPending) return false
        db.beginTransaction()
        try {
            val last = db.rawQuery(
                "SELECT value FROM app_meta WHERE name='remote_roster_schema_cursor'", null
            ).use { if (it.moveToFirst()) it.getString(0).toLongOrNull() ?: 0L else 0L }
            if (cursor <= last) return false
            RosterColumnDao.saveLayout(db, validated)
            db.execSQL("INSERT OR REPLACE INTO app_meta(name,value) VALUES('remote_roster_schema_cursor',?)",
                arrayOf(cursor.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return true
    }

    private fun customValues(roster: List<Employee>): Map<Pair<Long,String>,String> =
        RosterColumnDao.customValues(readableDatabase, roster)

    fun saveCustomValue(employeeId: Long, columnKey: String, value: String) {
        adminOnly()
        require(RosterColumnLayout.validate(rosterColumnLayout()).any { it.key==columnKey && it.custom }) {
            "لا يمكن تعديل عمود ثابت أو غير موجود"
        }
        require(value.length <= 200) { "القيمة طويلة جداً" }
        val employee=employees().firstOrNull { it.id==employeeId } ?: error("الفرد غير موجود")
        val db=writableDatabase
        db.beginTransaction()
        try {
            RosterColumnDao.saveValue(db,employeeId,columnKey,value)
            recordChange(db,"roster_custom_value","UPSERT","${employee.rosterNumber}:$columnKey",employee.site,
                JSONObject().put("roster_number",employee.rosterNumber).put("column",columnKey).put("value",value.trim()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun customValuesForEmployees(): Map<Pair<Long,String>,String> {
        adminOnly()
        return customValues(employees())
    }

    private fun createSyncOutbox(db: SQLiteDatabase) = SyncOutboxDao.createSchema(db)

    private fun recordChange(
        db: SQLiteDatabase, entity: String, operation: String,
        recordKey: String, site: String?, payload: JSONObject,
        actor: LocalSession = signedIn()
    ) = SyncOutboxDao.enqueue(db, actor, entity, operation, recordKey, site, payload)

    fun pendingSyncCount(): Int {
        signedIn()
        return SyncOutboxDao.pendingCount(readableDatabase)
    }

    fun pendingSyncEvents(limit: Int = 100): List<SyncPendingEvent> {
        signedIn()
        return SyncOutboxDao.pendingEvents(readableDatabase, limit)
    }

      
    fun acknowledgeSyncEvent(eventId: String) = SyncOutboxDao.acknowledge(writableDatabase, eventId)

    fun syncCursor(): Long {
        val session=signedIn()
        return SyncStateDao.cursor(readableDatabase,session.username,session.site?.let { stableSiteId(it) })
    }
    fun advanceSyncCursor(cursor: Long) {
        val session=signedIn()
        SyncStateDao.advanceCursor(writableDatabase,session.username,session.site?.let { stableSiteId(it) },cursor)
    }
    fun noteSyncFailure(eventId: String, message: String) {
        signedIn()
        SyncStateDao.noteError(writableDatabase,eventId,message)
    }

    fun requireNoPendingEdits() {
        check(pendingSyncCount() == 0) { "هناك تعديلات لم تُحفظ على السيرفر بعد، لا يمكن حذفها أو استبدالها" }
    }

      
    fun deleteEntireSiteRosters(siteNames: Set<String>): Int {
        adminOnly()
        require(siteNames.isNotEmpty()) { "اختر موقعاً واحداً على الأقل" }
        val db = writableDatabase
        val selected = siteNames.map(::cleanSiteName).toSet()
        require(selected.all { it in sites() }) { "موقع غير مسجل" }
        val linked = db.rawQuery("SELECT site FROM local_users WHERE role='SUPERVISOR'",null).use { c ->
            buildSet { while (c.moveToNext()) if (!c.isNull(0)) add(c.getString(0)) }
        }
        require(selected.none { it in linked }) { "لا يمكن حذف كشف موقع مرتبط بحساب مشرف؛ افصل أو انقل المشرف أولاً" }
        var count = 0
        db.beginTransaction()
        try {
            selected.forEach { site ->
                val entries = db.rawQuery("SELECT id,roster_number FROM employees WHERE site=?",arrayOf(site)).use { c ->
                    buildList { while(c.moveToNext()) add(c.getLong(0) to c.getString(1).orEmpty()) }
                }
                entries.forEach { (id,roster) ->
                    recordChange(db,"employees","DELETE",roster.ifBlank { "local:$id" },site,
                        JSONObject().put("local_id",id).put("roster_number",roster).put("site",site))
                }
                count += db.delete("employees", "site=?", arrayOf(site))
                
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count
    }

    private fun createRosterIndexes(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN roster_number TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_employees_roster_number ON employees(roster_number) WHERE roster_number <> ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_employees_site ON employees(site)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_employees_site_trim ON employees(trim(site))")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_employees_name ON employees(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_marks_employee_day ON marks(employee_id,day)")
    }

      
    private fun createDutyLink(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE local_users ADD COLUMN employee_id INTEGER")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_local_user_employee ON local_users(employee_id) WHERE employee_id IS NOT NULL")
    }

      
    fun supervisorDuty(session: LocalSession, date: LocalDate = LocalDate.now()): SupervisorDutyDecision {
        if (session.isAdmin) return SupervisorDutyDecision(DutyReason.AUTHORIZED)
        val link = readableDatabase.rawQuery(
            "SELECT u.employee_id, e.active, e.started, e.ended, e.site FROM local_users u LEFT JOIN employees e ON e.id=u.employee_id WHERE u.username=? AND u.role='SUPERVISOR' AND u.site=?",
            arrayOf(session.username, session.site.orEmpty())
        ).use { c ->
            if (!c.moveToFirst()) null
            else if (c.isNull(0)) Pair<Long?, Boolean>(null, false)
            else {
                val active = !c.isNull(1) && c.getInt(1) == 1 &&
                    !LocalDate.parse(c.getString(2)).isAfter(date) &&
                    (c.isNull(3) || !LocalDate.parse(c.getString(3)).isBefore(date))
                Pair(c.getLong(0), active)
            }
        }
        val enabled = readableDatabase.rawQuery("SELECT enabled FROM local_users WHERE username=? AND role='SUPERVISOR'",arrayOf(session.username)).use { c ->
            c.moveToFirst() && c.getInt(0) == 1
        }
        if (!enabled) return SupervisorDutyDecision(DutyReason.EMPLOYEE_INACTIVE)
        val id = link?.first
        val status = if (id == null) null else readableDatabase.rawQuery(
            "SELECT status FROM marks WHERE employee_id=? AND day=?", arrayOf(id.toString(),date.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        return SupervisorDutyPolicy.evaluate(id != null, link?.second == true, status,
            status?.let { Attendance.fromStored(it).label })
    }

    private fun requireDuty(db: SQLiteDatabase, session: LocalSession) {
        if (session.isAdmin) return
        
        val decision = supervisorDuty(session)
        if (!decision.allowed) throw SupervisorDutyDeniedException(
            "تم سحب صلاحيات التعديل منك. ${decision.message}")
    }

      
    fun requestDutyConfirmation(session: LocalSession, date: LocalDate = LocalDate.now()) {
        require(!session.isAdmin) { "هذا الطلب للمشرفين فقط" }
        val decision = supervisorDuty(session,date)
        require(decision.reason == DutyReason.NO_TODAY_MARK) { decision.message }
        val db = writableDatabase
        val title = "طلب إثبات حضور المشرف ${session.displayName}"
        db.execSQL("""INSERT INTO inbox_events(kind,actor_username,actor_label,recipient_username,site,title,detail)
            SELECT 'CHANGE',?,?,?,?,?,? WHERE NOT EXISTS (
                SELECT 1 FROM inbox_events WHERE kind='CHANGE' AND actor_username=? AND title=?
                AND detail LIKE ?
            )""", arrayOf(session.username,session.displayName,"ADMIN",session.site.orEmpty(), title,
            "طلب اعتماد حضور يوم $date • الحساب: ${session.username} • الموقع: ${session.site}",
            session.username,title,"%$date%"))
    }

    private fun createInboxTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS inbox_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL CHECK(kind IN ('CHANGE','MESSAGE')),
            actor_username TEXT NOT NULL, actor_label TEXT NOT NULL,
            recipient_username TEXT NOT NULL, site TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL, detail TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
            seen INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inbox_recipient ON inbox_events(recipient_username,kind,id)")
    }

      
    fun inbox(limit: Int = 300): List<LocalInboxEvent> {
        val session = signedIn()
        val selection = if (session.isAdmin) "kind='CHANGE'" else "kind='MESSAGE' AND recipient_username=?"
        val args = if (session.isAdmin) arrayOf(limit.toString()) else arrayOf(session.username,limit.toString())
        return readableDatabase.rawQuery(
            "SELECT id,kind,actor_username,actor_label,site,title,detail,created_at,seen FROM inbox_events WHERE $selection ORDER BY id DESC LIMIT ?",args
        ).use { c -> buildList { while (c.moveToNext()) add(LocalInboxEvent(
            c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),
            c.getString(5),c.getString(6),c.getString(7),c.getInt(8) != 0
        )) } }
    }

    fun unreadInboxCount(): Int {
        val session = signedIn()
        val condition = if (session.isAdmin) "kind='CHANGE'" else "kind='MESSAGE' AND recipient_username=?"
        val args = if (session.isAdmin) emptyArray<String>() else arrayOf(session.username)
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM inbox_events WHERE $condition AND seen=0",args).use { c ->
            c.moveToFirst(); c.getInt(0)
        }
    }

    fun markInboxSeen() {
        val session = signedIn()
        if (session.isAdmin) writableDatabase.execSQL("UPDATE inbox_events SET seen=1 WHERE kind='CHANGE' AND seen=0")
        else writableDatabase.execSQL("UPDATE inbox_events SET seen=1 WHERE kind='MESSAGE' AND recipient_username=? AND seen=0",arrayOf(session.username))
    }

      
    fun sendSupervisorMessage(text: String, site: String?): Int {
        adminOnly()
        val content = text.trim()
        require(content.isNotBlank() && content.length <= 2500) { "اكتب رسالة لا تتجاوز 2500 حرف" }
        if (site != null) require(site in sites()) { "الموقع المحدد غير موجود" }
        val session = signedIn()
        val db = writableDatabase
        val recipients = db.rawQuery(
            if (site == null) "SELECT username,site FROM local_users WHERE role='SUPERVISOR'"
            else "SELECT username,site FROM local_users WHERE role='SUPERVISOR' AND site=?",
            if (site == null) null else arrayOf(site)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
        require(recipients.isNotEmpty()) { "لا يوجد مشرفون مرتبطون بهذا الاختيار" }
        db.beginTransaction()
        try {
            recipients.forEach { (username, boundSite) ->
                db.execSQL("INSERT INTO inbox_events(kind,actor_username,actor_label,recipient_username,site,title,detail) VALUES('MESSAGE',?,?,?,?,?,?)",
                    arrayOf(session.username,"البشرية",username,boundSite,"رسالة من البشرية",content))
                recordChange(db,"messages","INSERT",java.util.UUID.randomUUID().toString(),boundSite,
                    JSONObject().put("recipient",username).put("site",boundSite).put("message",content))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return recipients.size
    }

      
    private fun createLocalAccounts(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS local_sites (name TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS local_users (username TEXT PRIMARY KEY NOT NULL, label TEXT NOT NULL, role TEXT NOT NULL, site TEXT, salt TEXT NOT NULL, secret TEXT NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO local_sites(name) SELECT DISTINCT trim(site) FROM employees WHERE trim(site)<>''")
        // SERVER: Create user profiles through authenticated provisioning, never through demo seeds.
    }

    // SERVER: Remove this temporary local administrator when Supabase Auth becomes authoritative.
    private fun ensureTemporaryAdmin(db: SQLiteDatabase, resetExisting: Boolean) {
        val existingRole = db.rawQuery(
            "SELECT role FROM local_users WHERE username=?", arrayOf("admin")
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (existingRole == null) {
            addAccount(db, "admin", "الإدارة العامة", "ADMIN", null, "1234567")
            return
        }
        require(existingRole == "ADMIN") { "اسم admin مستخدم بحساب لا يخص الإدارة العامة" }
        if (!resetExisting) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("label", "الإدارة العامة")
            putNull("site")
            put("enabled", 1)
            put("salt", salt.joinToString("") { "%02x".format(it.toInt() and 255) })
            put("secret", shaPassword("1234567", salt))
        }
        check(db.update("local_users", values, "username=? AND role='ADMIN'", arrayOf("admin")) == 1) {
            "تعذر إعداد حساب الإدارة المؤقت"
        }
    }

    private fun removeLegacyDemoCredentials(db: SQLiteDatabase) {
        // SERVER: Existing real accounts are retained; purge only unchanged known demo credentials.
        val demo = listOf("admin" to "Admin@1234", "supervisor1" to "123456", "supervisor2" to "123456")
        demo.forEach { (username, password) ->
            val result = db.rawQuery("SELECT salt,secret FROM local_users WHERE username=?", arrayOf(username)).use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
            } ?: return@forEach
            val salt = runCatching { result.first.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
                ?: return@forEach
            val derived = shaPassword(password, salt)
            if (MessageDigest.isEqual(derived.toByteArray(Charsets.US_ASCII), result.second.toByteArray(Charsets.US_ASCII))) {
                db.execSQL("DELETE FROM local_users WHERE username=?", arrayOf(username))
            }
        }
        for (name in listOf("موقع تجريبي 1", "موقع تجريبي 2")) {
            val unused = db.rawQuery("""SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM employees WHERE site=?)
                AND NOT EXISTS(SELECT 1 FROM local_users WHERE site=?)""", arrayOf(name, name)).use { it.moveToFirst() }
            if (unused) {
                db.execSQL("DELETE FROM local_sites WHERE name=?", arrayOf(name))
                db.execSQL("DELETE FROM site_identity WHERE name=?", arrayOf(name))
            }
        }
    }

    private fun shaPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it.toInt() and 255) } }
        finally { spec.clearPassword() }
    }

    private fun addAccount(db: SQLiteDatabase, name: String, label: String, role: String, site: String?, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("username", name); put("label", label); put("role", role); put("site", site)
            put("salt", salt.joinToString("") { "%02x".format(it.toInt() and 255) })
            put("secret", shaPassword(password, salt))
        }
        db.insertWithOnConflict("local_users", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun login(username: String, password: String): LocalSession? {
        val key = username.trim().lowercase(java.util.Locale.ROOT)
        val row = readableDatabase.rawQuery("SELECT username,label,role,site,salt,secret,enabled FROM local_users WHERE username=?", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) null else List(7) { i -> if (c.isNull(i)) "" else c.getString(i) }
        } ?: return null
        if (password.isBlank() || row[4].length % 2 != 0) return null
        val salt = runCatching { row[4].chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull() ?: return null
        val hash = shaPassword(password, salt)
        if (!MessageDigest.isEqual(hash.toByteArray(Charsets.US_ASCII), row[5].toByteArray(Charsets.US_ASCII))) return null
        val role = runCatching { LocalRole.valueOf(row[2]) }.getOrNull() ?: return null
        if (role == LocalRole.SUPERVISOR && row.getOrNull(6) != "1") return null
        return LocalSession(row[0], row[1], role, row[3].ifBlank { null })
    }

      
    private fun createSiteAliases(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS local_site_aliases (alias TEXT PRIMARY KEY NOT NULL, current_site TEXT NOT NULL)")
    }

    fun stableSiteId(name: String): String? {
        signedIn()
        return SiteIdentityDao.findId(readableDatabase,name)
    }
    fun stableSiteName(id: String): String? {
        signedIn()
        return SiteIdentityDao.findName(readableDatabase,id)
    }
    fun setSupervisorEnabled(username: String, enabled: Boolean) {
        adminOnly()
        val db=writableDatabase
        db.beginTransaction()
        try {
            require(db.rawQuery("SELECT 1 FROM local_users WHERE username=? AND role='SUPERVISOR'",arrayOf(username)).use { it.moveToFirst() }) { "المشرف غير موجود" }
            db.execSQL("UPDATE local_users SET enabled=? WHERE username=? AND role='SUPERVISOR'",arrayOf<Any>(if(enabled) 1 else 0, username))
            recordChange(db,"permissions","UPSERT",username,null,
                JSONObject().put("username",username).put("enabled",enabled))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun sites(): List<String> { adminOnly(); return readableDatabase.rawQuery("SELECT name FROM local_sites UNION SELECT DISTINCT trim(site) FROM employees WHERE trim(site)<>'' ORDER BY name",null).use { c ->
        buildList { while(c.moveToNext()) add(c.getString(0)) }
    } }
    fun siteDetails(): List<LocalSiteInfo> {
        adminOnly()
        val roster = allEmployees().groupBy { it.site.trim() }
        val supervisors = accounts().filter { it.role == LocalRole.SUPERVISOR }.groupBy { it.site.orEmpty() }
        return sites().map { site ->
            val people = roster[site].orEmpty()
            LocalSiteInfo(site, people.count { it.active }, people.size, supervisors[site].orEmpty().map { it.displayName })
        }
    }
    fun accounts(): List<LocalAccountInfo> { adminOnly(); return readableDatabase.rawQuery("SELECT username,label,role,site,employee_id,enabled FROM local_users ORDER BY role,username",null).use { c ->
        buildList { while(c.moveToNext()) add(LocalAccountInfo(c.getString(0),c.getString(1),LocalRole.valueOf(c.getString(2)), if(c.isNull(3)) null else c.getString(3), if (c.isNull(4)) null else c.getLong(4), c.getInt(5) == 1)) }
    } }
      
    fun supervisorEmployeeCandidates(site: String): List<Employee> {
        adminOnly()
        return employees().filter { it.active && it.ended == null }
    }

    fun certifySupervisorToday(username: String, status: Attendance): Boolean {
        adminOnly()
        val linked = accounts().firstOrNull { it.username == username && it.role == LocalRole.SUPERVISOR }
            ?: error("المشرف غير موجود")
        val id = linked.employeeId ?: error("اربط حساب المشرف بفرد من الكشف أولاً")
        require(status.name in statuses().map { it.name }) { "الحالة غير معرفة" }
        return applyAttendanceBatch(listOf(AttendanceBatch(setOf(id), status, LocalDate.now(), LocalDate.now()))) > 0
    }

    fun supervisorsActiveOnThisDevice(): Int { adminOnly(); return if (activeSession?.role == LocalRole.SUPERVISOR) 1 else 0 }

    private fun cleanSiteName(name: String): String = name.trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotBlank() && it.length <= 120) { "أدخل اسم موقع صالحاً (حتى 120 حرفاً)" }
    }
    private fun ensureSiteNameUnused(db: SQLiteDatabase, name: String) {
        require(name !in sites()) { "هذا الموقع مسجل بالفعل" }
        require(db.rawQuery("SELECT 1 FROM local_site_aliases WHERE alias=? LIMIT 1", arrayOf(name)).use { !it.moveToFirst() }) {
            "هذا الاسم مستخدم سابقاً كموقع تاريخي، اختر اسماً مختلفاً"
        }
    }
    fun addSite(name: String) {
        adminOnly()
        val clean = cleanSiteName(name)
        val db = writableDatabase
        ensureSiteNameUnused(db, clean)
        db.beginTransaction()
        try {
            db.insertOrThrow("local_sites",null, ContentValues().apply { put("name", clean) })
            SiteIdentityDao.add(db,clean)
            recordChange(db,"sites","UPSERT",clean,clean,JSONObject().put("name",clean))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

      
    fun renameSite(oldName: String, newName: String) {
        adminOnly()
        val clean = cleanSiteName(newName)
        require(oldName in sites()) { "الموقع غير موجود" }
        if (oldName == clean) return
        val db = writableDatabase
        ensureSiteNameUnused(db, clean)
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO local_sites(name) VALUES(?)", arrayOf(oldName))
            db.execSQL("INSERT INTO local_sites(name) VALUES(?)", arrayOf(clean))
            db.execSQL("UPDATE employees SET site=? WHERE trim(site)=?", arrayOf(clean, oldName))
            db.execSQL("UPDATE local_users SET site=? WHERE site=? AND role='SUPERVISOR'", arrayOf(clean, oldName))
            
            SiteIdentityDao.rename(db,oldName,clean)
            db.execSQL("UPDATE local_site_aliases SET current_site=? WHERE current_site=?", arrayOf(clean, oldName))
            db.execSQL("INSERT OR REPLACE INTO local_site_aliases(alias,current_site) VALUES(?,?)", arrayOf(oldName,clean))
            db.delete("local_sites", "name=?", arrayOf(oldName))
            recordChange(db,"sites","RENAME",oldName,clean,JSONObject().put("old",oldName).put("name",clean))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun checkedSupervisor(username: String, label: String, password: String?, site: String) {
        require(username.matches(Regex("[a-z][a-z0-9_]{2,31}"))) { "اسم المستخدم: حروف إنجليزية وأرقام، من 3 إلى 32" }
        require(label.trim().isNotBlank() && label.trim().length <= 90) { "اكتب اسم المشرف (حتى 90 حرفاً)" }
        require(password == null || SupervisorPasswordPolicy.isValid(password)) { SupervisorPasswordPolicy.guidance }
        require(site.isNotBlank() && site in sites()) { "اختر موقعاً مسجلاً" }
    }
    private fun verifySupervisorEmployee(db: SQLiteDatabase, employeeId: Long, site: String, label: String, usernameToExclude: String? = null) {
        val match = db.rawQuery("SELECT 1 FROM employees WHERE id=? AND active=1 AND ended IS NULL AND started<=? LIMIT 1",
            arrayOf(employeeId.toString(), LocalDate.now().toString())).use { it.moveToFirst() }
        require(match) { "اختر فرداً نشطاً من الكشف العام" }
        val actualName = db.rawQuery("SELECT name FROM employees WHERE id=?", arrayOf(employeeId.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }
        require(actualName.trim() == label.trim()) { "اسم المشرف لا يطابق الفرد المختار من الكشف؛ اختره من الاقتراحات" }
        val occupied = db.rawQuery(
            "SELECT username FROM local_users WHERE employee_id=? LIMIT 1",arrayOf(employeeId.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        require(occupied == null || occupied == usernameToExclude) { "هذا الفرد مربوط بحساب مشرف آخر" }
    }

    fun addSupervisor(username: String, label: String, password: String, site: String, employeeId: Long) {
        adminOnly()
        val key = username.trim().lowercase(java.util.Locale.ROOT)
        checkedSupervisor(key,label,password,site)
        val db = writableDatabase
        require(db.rawQuery("SELECT 1 FROM local_users WHERE username=?", arrayOf(key)).use { !it.moveToFirst() }) { "اسم المستخدم موجود مسبقاً" }
        verifySupervisorEmployee(db, employeeId, site, label)
        db.beginTransaction()
        try {
            addAccount(db, key, label.trim(), "SUPERVISOR", site, password)
            db.execSQL("UPDATE local_users SET employee_id=? WHERE username=? AND role='SUPERVISOR'",
                arrayOf<Any>(employeeId,key))
            recordChange(db,"supervisors","UPSERT",key,site,
                JSONObject().put("username",key).put("label",label.trim()).put("site",site)
                    .put("employee_local_id",employeeId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
      
    fun updateSupervisor(originalUsername: String, username: String, label: String, password: String, site: String, employeeId: Long) {
        adminOnly()
        val key = username.trim().lowercase(java.util.Locale.ROOT)
        checkedSupervisor(key, label, password.takeIf { it.isNotEmpty() }, site)
        val db = writableDatabase
        require(db.rawQuery("SELECT 1 FROM local_users WHERE username=? AND role='SUPERVISOR'",arrayOf(originalUsername)).use { it.moveToFirst() }) { "المشرف لم يعد موجوداً" }
        require(db.rawQuery("SELECT 1 FROM local_users WHERE username=? AND username<>?",arrayOf(key,originalUsername)).use { !it.moveToFirst() }) { "اسم المستخدم مستخدم" }
        verifySupervisorEmployee(db, employeeId, site, label, originalUsername)
        val values = ContentValues().apply {
            put("username", key); put("label", label.trim()); put("site", site); put("employee_id", employeeId)
            if (password.isNotEmpty()) {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                put("salt", salt.joinToString("") { "%02x".format(it.toInt() and 255) })
                put("secret", shaPassword(password,salt))
            }
        }
        db.beginTransaction()
        try {
            require(db.update("local_users", values, "username=? AND role='SUPERVISOR'", arrayOf(originalUsername)) == 1) { "تعذر تحديث حساب المشرف" }
            
            if (originalUsername != key) db.execSQL("UPDATE inbox_events SET recipient_username=? WHERE kind='MESSAGE' AND recipient_username=?",
                arrayOf(key,originalUsername))
            recordChange(db,"supervisors","UPSERT",key,site,
                JSONObject().put("old_username",originalUsername).put("username",key)
                    .put("label",label.trim()).put("site",site).put("employee_local_id",employeeId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun removeSupervisor(username: String) {
        adminOnly()
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(db.delete("local_users", "username=? AND role='SUPERVISOR'",arrayOf(username)) == 1) { "الحساب غير موجود" }
            
            db.delete("inbox_events", "kind='MESSAGE' AND recipient_username=?",arrayOf(username))
            recordChange(db,"supervisors","DELETE",username,null,JSONObject().put("username",username))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun removeSite(name: String) {
        adminOnly()
        require(name in sites()) { "الموقع غير موجود" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            // SERVER: A deleted site must be retired and its employees made unassigned atomically.
            val affectedEmployees = db.rawQuery(
                "SELECT COUNT(*) FROM employees WHERE trim(site)=?", arrayOf(name)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val affectedSupervisors = db.rawQuery(
                "SELECT COUNT(*) FROM local_users WHERE role='SUPERVISOR' AND site=?", arrayOf(name)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            db.execSQL("UPDATE employees SET site='' WHERE trim(site)=?", arrayOf(name))
            db.execSQL("UPDATE local_users SET site=NULL, enabled=0 WHERE role='SUPERVISOR' AND site=?", arrayOf(name))
            db.delete("local_site_aliases", "current_site=?", arrayOf(name))
            require(db.delete("local_sites", "name=?", arrayOf(name)) == 1) { "تعذر حذف الموقع" }
            SiteIdentityDao.retire(db,name)
            recordChange(db,"sites","DELETE",name,name,JSONObject()
                .put("name",name).put("unassigned_employees",affectedEmployees)
                .put("disabled_supervisors",affectedSupervisors))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun createStatuses(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attendance_statuses (code TEXT PRIMARY KEY, title TEXT NOT NULL, symbol TEXT NOT NULL, color INTEGER NOT NULL, position INTEGER NOT NULL)")
        Attendance.defaults.forEachIndexed { index, item ->
            db.execSQL("INSERT OR IGNORE INTO attendance_statuses(code,title,symbol,color,position) VALUES(?,?,?,?,?)",
                arrayOf<Any>(item.name, item.label, item.symbol, item.argb, index))
        }
    }

    fun statuses(): List<Attendance> = readableDatabase.rawQuery(
        "SELECT code,title,symbol,color FROM attendance_statuses ORDER BY position,code", null
    ).use { c -> buildList {
        while (c.moveToNext()) add(Attendance(c.getString(0), c.getString(2), c.getString(1), c.getLong(3)))
    } }

      
    fun saveStatus(code: String?, label: String, symbol: String, color: Long): Attendance {
        adminOnly()
        val title = label.trim(); val abbrev = symbol.trim()
        require(title.isNotBlank() && title.length <= 32) { "اسم الحالة مطلوب (حتى 32 حرفاً)" }
        require(abbrev.isNotBlank() && abbrev.length <= 3) { "رمز الحالة مطلوب (حتى 3 أحرف)" }
        require((color ushr 24) == 0xFFL) { "اللون يجب أن يكون معتماً" }
        val current = statuses()
        require(current.none { it.name != code && it.label.equals(title, ignoreCase = true) }) { "اسم الحالة مستخدم" }
        require(current.none { it.name != code && it.symbol == abbrev }) { "رمز الحالة مستخدم" }
        require(current.size < 32 || code != null) { "الحد الأقصى 32 حالة" }
        val id = code ?: "CUSTOM_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        require(code == null || current.any { it.name == code }) { "الحالة المراد تعديلها غير موجودة" }
        val values = ContentValues().apply { put("code", id); put("title", title)
            put("symbol", abbrev); put("color", color); put("position", current.firstOrNull { it.name == code }?.let { current.indexOf(it) } ?: current.size) }
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("attendance_statuses", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            recordChange(db,"statuses","UPSERT",id,null,JSONObject().put("code",id)
                .put("title",title).put("symbol",abbrev).put("color",color))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        Attendance.configure(statuses())
        return Attendance.fromStored(id)
    }

      
    fun periodReport(ids: Set<Long>, from: LocalDate, to: LocalDate,
                     today: LocalDate = LocalDate.now()): PeriodReport =
        AttendanceReportQueries.periodReport(signedIn(),ids,employees(),from,to,today,
            loadMonth={month(it)},loadArchive={archivedMonth(it)})

    fun employeeHistory(employeeId: Long, today: LocalDate = LocalDate.now()): List<EmployeeMonthReport> =
        AttendanceReportQueries.history(employeeId,employees(),archivedMonths(),today,
            loadMonth={month(it)},loadArchive={archivedMonth(it)})

    private fun createArchiveTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS monthly_archives (month TEXT PRIMARY KEY, created TEXT NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_meta (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    private fun setArchiveStart(db: SQLiteDatabase, month: YearMonth) {
        val value = ContentValues().apply { put("name", "archive_since"); put("value", month.toString()) }
        db.insertWithOnConflict("app_meta", null, value, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun inferLegacyStart(db: SQLiteDatabase, now: YearMonth): YearMonth {
        
        
        val firstMark = db.rawQuery("SELECT MIN(day) FROM marks", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
        val firstEmployee = db.rawQuery("SELECT MIN(started) FROM employees", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
        val candidate = listOfNotNull(firstMark, firstEmployee)
            .mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() }
            .minOrNull() ?: now
        return maxOf(candidate, now.minusMonths(24)).coerceAtMost(now)
    }

    fun archiveCompletedMonths(today: LocalDate = LocalDate.now()): Int {
        val current = YearMonth.from(today)
        val db = writableDatabase
        val start = db.rawQuery("SELECT value FROM app_meta WHERE name='archive_since'", null).use {
            if (it.moveToFirst()) YearMonth.parse(it.getString(0)) else current
        }
        if (!start.isBefore(current)) return 0
        
        val saved = db.rawQuery("SELECT month FROM monthly_archives", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        val pending = mutableListOf<YearMonth>()
        var m = start
        while (m.isBefore(current)) {
            if (m.toString() !in saved) pending.add(m)
            m = m.plusMonths(1)
        }
        if (pending.isEmpty()) return 0
        var created = 0
        db.beginTransaction()
        try {
            
            for (month in pending) {
                val record = fullMonth(month)
                val payload = ArchiveSnapshot.encode(record)
                db.execSQL(
                    "INSERT OR IGNORE INTO monthly_archives(month,created,payload) VALUES(?,datetime('now'),?)",
                    arrayOf(month.toString(), payload)
                )
                created++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return created
    }

    private fun archiveForSession(data: MonthData, session: LocalSession): MonthData {
        if (session.isAdmin) return data
        val current = session.site!!
        val siteNames = readableDatabase.rawQuery(
            "SELECT alias FROM local_site_aliases WHERE current_site=?", arrayOf(current)
        ).use { c -> buildSet {
            add(current)
            while (c.moveToNext()) add(c.getString(0))
        } }
        val selected = data.employees.filter { it.site.trim() in siteNames }
        val ids = selected.mapTo(HashSet()) { it.id }
        return data.copy(employees=selected, marks=data.marks.filterKeys { it.first in ids },
            columnValues=data.columnValues.filterKeys { it.first in ids })
    }

    fun archivedMonths(): List<YearMonth> {
        val session = signedIn()
        if (session.isAdmin) return readableDatabase.rawQuery(
            "SELECT month FROM monthly_archives ORDER BY month DESC", null
        ).use { c -> buildList { while (c.moveToNext()) add(YearMonth.parse(c.getString(0))) } }
        
        return readableDatabase.rawQuery(
            "SELECT month,payload FROM monthly_archives ORDER BY month DESC", null
        ).use { c -> buildList {
            while(c.moveToNext()) {
                val snapshot = ArchiveSnapshot.decode(c.getString(1))
                if (archiveForSession(snapshot, session).employees.isNotEmpty()) add(YearMonth.parse(c.getString(0)))
            }
        } }
    }

    fun archivedMonth(month: YearMonth): MonthData? {
        val session = signedIn()
        return readableDatabase.rawQuery(
            "SELECT payload FROM monthly_archives WHERE month=?", arrayOf(month.toString())
        ).use { c -> if (c.moveToFirst()) archiveForSession(ArchiveSnapshot.decode(c.getString(0)), session) else null }
    }

    private fun Cursor.employee() = Employee(getLong(0), getString(1), getString(2), LocalDate.parse(getString(3)), if (isNull(4)) null else LocalDate.parse(getString(4)), getInt(5) == 1, getString(6).orEmpty(), getString(7).orEmpty())

    private fun allEmployees(): List<Employee> = EmployeeOrder.employees(readableDatabase.rawQuery("SELECT id,code,name,started,ended,active,site,roster_number FROM employees ORDER BY id", null).use { c -> buildList { while (c.moveToNext()) add(c.employee()) } })

    fun employees(): List<Employee> {
        val session = signedIn()
        if (session.isAdmin) return allEmployees()
        return EmployeeOrder.employees(readableDatabase.rawQuery(
            "SELECT id,code,name,started,ended,active,site,roster_number FROM employees WHERE trim(site)=? ORDER BY id",
            arrayOf(session.site!!)
        ).use { c -> buildList { while(c.moveToNext()) add(c.employee()) } })
    }

    private fun resolvedMarksBetween(start: LocalDate, end: LocalDate, roster: List<Employee>):
        Map<Pair<Long, LocalDate>, Attendance> {
        if (roster.isEmpty()) return emptyMap()
        val ids = roster.mapTo(HashSet()) { it.id }
        val database = readableDatabase
        val prior = database.rawQuery(
            "SELECT m.employee_id,m.status FROM marks m JOIN " +
                "(SELECT employee_id,MAX(day) last_day FROM marks WHERE day<? GROUP BY employee_id) t " +
                "ON t.employee_id=m.employee_id AND t.last_day=m.day",
            arrayOf(start.toString())
        ).use { c -> buildMap {
            while (c.moveToNext()) if (c.getLong(0) in ids)
                put(c.getLong(0), Attendance.fromStored(c.getString(1)))
        } }
        val explicit = database.rawQuery(
            "SELECT employee_id,day,status FROM marks WHERE day>=? AND day<=?",
            arrayOf(start.toString(),end.toString())
        ).use { c -> buildMap {
            while (c.moveToNext()) if (c.getLong(0) in ids)
                put(c.getLong(0) to LocalDate.parse(c.getString(1)),
                    Attendance.fromStored(c.getString(2)))
        } }
        return DailyCarryForward.resolve(ids, start, end, LocalDate.now(), prior, explicit)
    }

    private fun allMarksBetween(start: LocalDate, end: LocalDate): Map<Pair<Long, LocalDate>, Attendance> =
        resolvedMarksBetween(start, end, allEmployees())

    fun marksBetween(start: LocalDate, end: LocalDate): Map<Pair<Long, LocalDate>, Attendance> =
        resolvedMarksBetween(start, end, employees())

      
    private fun fullMonth(month: YearMonth): MonthData {
        val roster = allEmployees().filter { it.overlaps(month) }
        return MonthData(month, roster, resolvedMarksBetween(month.atDay(1), month.atEndOfMonth(), roster),customValues(roster))
    }

    fun month(month: YearMonth): MonthData {
        val roster = employees().filter { it.overlaps(month) }
        return MonthData(month, roster, resolvedMarksBetween(month.atDay(1), month.atEndOfMonth(), roster),customValues(roster))
    }
    fun dayStats(day: LocalDate): DayStats = AttendanceRules.count(employees(), marksBetween(day, day), day)

    fun nextCode(): String {
        adminOnly()
        val all = employees().mapNotNull { it.code.toLongOrNull() }
        return ((all.maxOrNull() ?: 0L) + 1L).toString().padStart(3, '0')
    }

    fun addEmployee(name: String, code: String, started: LocalDate, site: String = "", rosterNumber: String): Long {
        adminOnly()
        require(site.trim() in sites()) { "الموقع غير مسجل؛ أضفه من إدارة المواقع أولاً" }
        require(name.isNotBlank() && code.isNotBlank())
        val roster = RosterNumbers.clean(rosterNumber)
        require(employees().none { it.rosterNumber == roster }) { "الرقم (ع) مستخدم مسبقاً" }
        require(employees().none { it.code == code.trim() }) { "الرقم الوظيفي مستخدم مسبقاً" }
        val v = ContentValues().apply { put("name", EmployeeOrder.clean(name)); put("code", code.trim()); put("roster_number", roster); put("started", started.toString()); put("site", site.trim().take(120)); put("active", 1) }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val id=db.insertOrThrow("employees", null, v)
            recordChange(db,"employees","UPSERT",roster,site.trim(), JSONObject()
                .put("roster_number",roster).put("local_id",id).put("name",EmployeeOrder.clean(name))
                .put("site",site.trim()).put("code",code.trim()).put("started",started.toString()).put("active",true))
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun editEmployee(employee: Employee, name: String, code: String, started: LocalDate, site: String = "", rosterNumber: String) {
        adminOnly()
        require(site.trim() in sites()) { "الموقع غير مسجل؛ أضفه من إدارة المواقع أولاً" }
        require(name.isNotBlank() && code.isNotBlank())
        val roster = RosterNumbers.clean(rosterNumber)
        require(employees().none { it.id != employee.id && it.rosterNumber == roster }) { "الرقم (ع) مستخدم مسبقاً" }
        require(employees().none { it.id != employee.id && it.code == code.trim() }) { "الرقم الوظيفي مستخدم مسبقاً" }
        val v = ContentValues().apply { put("name", EmployeeOrder.clean(name)); put("code", code.trim()); put("roster_number", roster); put("started", started.toString()); put("site", site.trim().take(120)) }
        val db=writableDatabase
        db.beginTransaction()
        try {
            check(db.update("employees", v, "id=?", arrayOf(employee.id.toString()))==1)
            recordChange(db,"employees","UPSERT",roster,site.trim(),JSONObject()
                .put("roster_number",roster).put("local_id",employee.id)
                .put("name",EmployeeOrder.clean(name)).put("site",site.trim())
                .put("code",code.trim()).put("started",started.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun updateEmployeesBatch(edits: List<Employee>): Int {
        adminOnly()
        if (edits.isEmpty()) return 0
        require(edits.map { it.id }.distinct().size == edits.size) { "تكرر الفرد في طلب التعديل" }
        val original = employees().associateBy { it.id }
        require(edits.all { it.id in original }) { "تعذر العثور على أحد الافراد" }
        val normalized = edits.map { it.copy(
            name = EmployeeOrder.clean(it.name), code = it.code.trim(), site = it.site.trim().take(120),
            rosterNumber = it.rosterNumber.takeIf(String::isNotBlank)?.let(RosterNumbers::clean) ?: ""
        ) }
        val finalRoster = original.toMutableMap().apply { normalized.forEach { put(it.id, it) } }.values
        require(finalRoster.all { it.name.isNotBlank() && it.code.isNotBlank() }) { "اسم الفرد ورقمه مطلوبان" }
        val registeredSites = sites().toHashSet()
        require(normalized.all { it.site in registeredSites }) {
            "تحتوي التعديلات على موقع غير مسجل؛ أضفه أولاً من إدارة المواقع"
        }
        
        require(finalRoster.map { it.rosterNumber }.filter { it.isNotBlank() }.distinct().size == finalRoster.count { it.rosterNumber.isNotBlank() }) { "هناك أرقام (ع) مكررة" }
        require(finalRoster.map { it.code }.distinct().size == finalRoster.size) { "هناك أرقام وظيفية مكررة" }
        require(normalized.all { it.ended == null || !it.ended.isBefore(it.started) }) { "تاريخ الالتحاق يتجاوز نهاية الخدمة" }
        val linked = readableDatabase.rawQuery("SELECT employee_id,site FROM local_users WHERE employee_id IS NOT NULL",null).use { c ->
            buildMap { while(c.moveToNext()) put(c.getLong(0),c.getString(1)) }
        }
        require(normalized.none { e -> e.id in linked && (e.site != linked[e.id]) }) {
            "لا يمكن نقل المشرف أو إنهاء خدمته قبل تعديل ارتباط حسابه من إدارة المشرفين"
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            
            val changedRoster = normalized.filter { original[it.id]?.rosterNumber != it.rosterNumber && original[it.id]?.rosterNumber?.isNotBlank() == true }
            changedRoster.forEach { e -> db.execSQL("UPDATE employees SET roster_number='' WHERE id=?", arrayOf(e.id)) }
            val changedCodes = normalized.filter { original[it.id]?.code != it.code }
            changedCodes.forEach { e ->
                db.execSQL("UPDATE employees SET code=? WHERE id=?",
                    arrayOf<Any>("__manger_pending_${java.util.UUID.randomUUID()}", e.id))
            }
            normalized.forEach { e ->
                val values = ContentValues().apply {
                    put("name", e.name); put("code", e.code); put("roster_number", e.rosterNumber)
                    put("site", e.site); put("started", e.started.toString())
                    put("active", if (e.active) 1 else 0)
                    if (e.ended == null) putNull("ended") else put("ended", e.ended.toString())
                }
                check(db.update("employees", values, "id=?", arrayOf(e.id.toString())) == 1) {
                    "تعذر حفظ تعديل الفرد: ${e.name}"
                }
                recordChange(db,"employees","UPSERT",e.rosterNumber.ifBlank { "local:${e.id}" },e.site,
                    JSONObject().put("local_id",e.id).put("roster_number",e.rosterNumber).put("name",e.name)
                        .put("site",e.site).put("code",e.code).put("active",e.active))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return normalized.size
    }

    fun deleteEmployee(id: Long) {
        adminOnly()
        require(readableDatabase.rawQuery("SELECT 1 FROM local_users WHERE employee_id=? LIMIT 1", arrayOf(id.toString())).use { !it.moveToFirst() }) {
            "هذا الفرد مرتبط بحساب مشرف، غيّر ارتباط المشرف أولاً"
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val employee = db.rawQuery("SELECT roster_number,site FROM employees WHERE id=?",arrayOf(id.toString())).use { c ->
                if (!c.moveToFirst()) error("الفرد غير موجود")
                c.getString(0).orEmpty() to c.getString(1).orEmpty()
            }
            require(db.delete("employees", "id=?", arrayOf(id.toString())) == 1) { "الفرد غير موجود" }
            recordChange(db,"employees","DELETE",employee.first.ifBlank { "local:$id" },employee.second,
                JSONObject().put("local_id",id).put("roster_number",employee.first).put("site",employee.second))
            db.delete("audit", "employee_id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun setArchived(employee: Employee, archive: Boolean) {
        adminOnly()
        val today = LocalDate.now()
        val v = ContentValues().apply { put("active", if (archive) 0 else 1); if (archive) put("ended", today.toString()) else putNull("ended") }
        val db=writableDatabase
        db.beginTransaction()
        try {
            check(db.update("employees", v, "id=?", arrayOf(employee.id.toString()))==1)
            recordChange(db,"employees","UPSERT",employee.rosterNumber.ifBlank { "local:${employee.id}" }, employee.site,
                JSONObject().put("local_id",employee.id).put("roster_number",employee.rosterNumber)
                    .put("active", !archive).put("ended",if(archive) today.toString() else JSONObject.NULL))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun applyAttendance(ids: Set<Long>, status: Attendance, start: LocalDate, end: LocalDate): Int =
        applyAttendanceBatch(listOf(AttendanceBatch(ids, status, start, end)))

      
    fun applyAttendanceBatch(groups: List<AttendanceBatch>): Int {
        val actor = signedIn()
        requireDuty(readableDatabase,actor)
        requireAttendanceTime(actor)
        val visible = employees()
        val allowedIds = visible.mapTo(HashSet()) { it.id }
        require(groups.all { group -> group.employeeIds.all { it in allowedIds } }) {
            "لا يسمح بالتحضير لفرد خارج موقع الحساب"
        }
        val changes = AttendanceBatchPlanner.plan(groups, visible)
        val registered = statuses().map { it.name }.toSet()
        require(changes.values.all { it.name in registered }) { "الحالة المختارة لم تعد موجودة" }
        if (!actor.isAdmin) {
            require(groups.none { it.status.name == AttendanceLeavePolicy.LEAVE_CODE }) {
                "إضافة حالة إجازة متاحة للإدارة العامة فقط"
            }
        }
        if (!actor.isAdmin) {
            val selfId = readableDatabase.rawQuery("SELECT employee_id FROM local_users WHERE username=?", arrayOf(actor.username)).use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
            require(changes.keys.none { it.first == selfId }) { "تعديل تحضير المشرف نفسه متاح للإدارة فقط" }
        }
        require(changes.keys.none { (_, day) -> day.isBefore(YearMonth.now().atDay(1)) }) {
            "الأشهر المكتملة محفوظة في الأرشيف ولا يمكن تعديلها من التحضير"
        }
        val db = writableDatabase
        var changed = 0
        val changesByEmployee = linkedMapOf<Long, MutableList<String>>()
        db.beginTransaction()
        try {
            requireDuty(db,actor)
            requireAttendanceTime(actor)
            for ((key, status) in changes) {
                val (employeeId, day) = key
                val whereArgs = arrayOf(employeeId.toString(), day.toString())
                val stored = db.rawQuery(
                    "SELECT status FROM marks WHERE employee_id=? AND day=?", whereArgs
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                val previous = stored ?: db.rawQuery(
                    "SELECT status FROM marks WHERE employee_id=? AND day<? ORDER BY day DESC LIMIT 1",
                    arrayOf(employeeId.toString(), day.toString())
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "PRESENT" }
                AttendanceLeavePolicy.requireAllowed(actor.isAdmin, previous, status.name)

                if (stored != status.name) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO marks(employee_id,day,status,changed) VALUES(?,?,?,datetime('now'))",
                        arrayOf<Any>(employeeId, day.toString(), status.name)
                    )
                    db.execSQL(
                        "INSERT INTO audit(employee_id,day,before_state,after_state) VALUES(?,?,?,?)",
                        arrayOf<Any>(employeeId, day.toString(), previous, status.name)
                    )
                    val employee = visible.first { it.id == employeeId }
                    recordChange(db,"attendance","UPSERT","${employee.rosterNumber}:$day",employee.site,
                        JSONObject().put("roster_number",employee.rosterNumber).put("local_id",employeeId)
                            .put("date",day.toString()).put("status",status.name))
                    changed++
                    if (!actor.isAdmin) {
                        changesByEmployee.getOrPut(employeeId) { mutableListOf() }.add(
                            "$day: من ${Attendance.fromStored(previous).label} إلى ${status.label}"
                        )
                    }
                }
            }
            if (!actor.isAdmin) {
                val byId = visible.associateBy { it.id }
                changesByEmployee.forEach { (employeeId, edits) ->
                    val employee = byId[employeeId] ?: return@forEach
                    db.execSQL("INSERT INTO inbox_events(kind,actor_username,actor_label,recipient_username,site,title,detail) VALUES('CHANGE',?,?,?,?,?,?)",
                        arrayOf(actor.username,actor.displayName,"ADMIN",actor.site.orEmpty(),
                            "تعديل تحضير: ${employee.name}",
                            "الفرد: ${employee.name} • الرقم: ${employee.code} • الموقع: ${employee.site}\n" + edits.joinToString("\n")))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return changed
    }

    fun audit(limit: Int = 200): List<Audit> { adminOnly(); return readableDatabase.rawQuery("SELECT id,employee_id,day,before_state,after_state,changed FROM audit ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c -> buildList { while(c.moveToNext()) add(Audit(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))) } } }

      
    private fun verifyImportSites(records: List<ImportedEmployee>) {
        val allowed = sites().toHashSet()
        val unknown = records.map { it.site.trim() }.filter { it !in allowed }.distinct()
        require(unknown.isEmpty()) {
            "مواقع غير مسجلة في الكشف: ${unknown.take(6).joinToString("، ")}. أضفها من إدارة المواقع أولاً"
        }
    }

    fun checkImport(records: List<ImportedEmployee>): ImportCheck {
        adminOnly()
        verifyImportSites(records)
        return RosterImportValidator.inspect(records, employees())
    }

    fun importEmployeesWithSites(records: List<ImportedEmployee>, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportSummary {
        adminOnly()
        val submittingAdmin = signedIn()
        val existing = employees()
        
        verifyImportSites(records)
        val check = RosterImportValidator.inspect(records, existing)
        return RosterImportDao.importRoster(writableDatabase, records, existing, check, onProgress) { database, number, site, payload ->
            recordChange(database, "employees", "UPSERT", number, site, payload, submittingAdmin)
        }
    }

    fun backup(): String {
        adminOnly()
        return buildBackupPayload()
    }

      
    fun backupForInternalStorage(): String = buildBackupPayload()

    private fun buildBackupPayload(): String {
        val root = JSONObject().put("format", "manger-backup").put("version", 12).put("created", java.time.LocalDateTime.now().toString())
        val es = JSONArray()
        allEmployees().forEach { e -> es.put(JSONObject().put("id",e.id).put("code",e.code).put("roster_number",e.rosterNumber).put("name",e.name).put("site",e.site).put("started",e.started.toString()).put("ended",e.ended?.toString() ?: JSONObject.NULL).put("active",e.active)) }
        root.put("employees",es)
        root.put("roster_layout",RosterColumnLayout.encode(rosterColumnLayout()))
        root.put("attendance_window", attendanceWindow().serialize())
        root.put("employee_custom_values", JSONArray().apply {
            readableDatabase.rawQuery("SELECT employee_id,column_key,value FROM employee_custom_values",null).use { c ->
                while(c.moveToNext()) put(JSONObject().put("employee_id",c.getLong(0)).put("column_key",c.getString(1)).put("value",c.getString(2)))
            }
        })
        val allMarks = JSONArray()
        readableDatabase.rawQuery("SELECT employee_id,day,status FROM marks",null).use { c -> while (c.moveToNext()) allMarks.put(JSONObject().put("employee_id",c.getLong(0)).put("day",c.getString(1)).put("status",c.getString(2))) }
        root.put("marks",allMarks)
        root.put("statuses", JSONArray().apply {
            statuses().forEachIndexed { index, status ->
                put(JSONObject().put("code", status.name).put("title", status.label)
                    .put("symbol", status.symbol).put("color", status.argb).put("position", index))
            }
        })
        val logs = JSONArray()
        readableDatabase.rawQuery("SELECT employee_id,day,before_state,after_state,changed FROM audit",null).use { c -> while(c.moveToNext()) logs.put(JSONObject().put("employee_id",c.getLong(0)).put("day",c.getString(1)).put("before",c.getString(2)).put("after",c.getString(3)).put("changed",c.getString(4))) }
        root.put("audit",logs)
        val archived = JSONArray()
        readableDatabase.rawQuery("SELECT month,created,payload FROM monthly_archives ORDER BY month", null).use { c ->
            while (c.moveToNext()) archived.put(JSONObject().put("month", c.getString(0))
                .put("created", c.getString(1)).put("payload", JSONObject(c.getString(2))))
        }
        root.put("archives",archived)
        val since = readableDatabase.rawQuery("SELECT value FROM app_meta WHERE name='archive_since'", null).use {
            if (it.moveToFirst()) it.getString(0) else YearMonth.now().toString()
        }
        root.put("archive_since", since)
        root.put("sites", JSONArray().apply {
            readableDatabase.rawQuery("SELECT name FROM local_sites UNION SELECT DISTINCT trim(site) FROM employees WHERE trim(site)<>'' ORDER BY name", null).use { c ->
                while (c.moveToNext()) put(c.getString(0))
            }
        })
        root.put("site_identity", JSONArray().apply {
            readableDatabase.rawQuery("SELECT site_id,name,retired FROM site_identity",null).use { c ->
                while(c.moveToNext()) put(JSONObject().put("id",c.getString(0)).put("name",c.getString(1)).put("retired",c.getInt(2)))
            }
        })
        root.put("site_aliases", JSONArray().apply {
            readableDatabase.rawQuery("SELECT alias,current_site FROM local_site_aliases", null).use { c ->
                while (c.moveToNext()) put(JSONObject().put("alias", c.getString(0)).put("current_site", c.getString(1)))
            }
        })
        root.put("accounts", JSONArray().apply {
            readableDatabase.rawQuery("SELECT username,label,role,site,salt,secret,employee_id,enabled FROM local_users", null).use { c ->
                while (c.moveToNext()) put(JSONObject()
                    .put("username", c.getString(0)).put("label", c.getString(1))
                    .put("role", c.getString(2)).put("site", if (c.isNull(3)) JSONObject.NULL else c.getString(3))
                    .put("salt", c.getString(4)).put("secret", c.getString(5))
                    .put("employee_id", if (c.isNull(6)) JSONObject.NULL else c.getLong(6))
                    .put("enabled", c.getInt(7) == 1))
            }
        })
        root.put("inbox_events",JSONArray().apply {
            readableDatabase.rawQuery("SELECT kind,actor_username,actor_label,recipient_username,site,title,detail,created_at,seen FROM inbox_events ORDER BY id",null).use { c ->
                while (c.moveToNext()) put(JSONObject().put("kind",c.getString(0)).put("actor_username",c.getString(1))
                    .put("actor_label",c.getString(2)).put("recipient_username",c.getString(3))
                    .put("site",c.getString(4)).put("title",c.getString(5)).put("detail",c.getString(6))
                    .put("created_at",c.getString(7)).put("seen",c.getInt(8)))
            }
        })
        root.put("sync_outbox",JSONArray().apply {
            readableDatabase.rawQuery("SELECT event_id,actor_username,actor_role,entity,operation,record_key,site,payload,created_at FROM sync_outbox ORDER BY rowid", null).use { c ->
                while(c.moveToNext()) put(JSONObject().put("event_id",c.getString(0))
                    .put("actor",c.getString(1)).put("role",c.getString(2)).put("entity",c.getString(3))
                    .put("operation",c.getString(4)).put("record_key",c.getString(5))
                    .put("site",if(c.isNull(6)) JSONObject.NULL else c.getString(6))
                    .put("payload",c.getString(7)).put("created_at",c.getString(8)))
            }
        })
        return root.toString(2)
    }

    fun restore(json: String) {
        adminOnly()
        val root = JSONObject(json)
        require(root.optString("format") == "manger-backup" && root.optInt("version") in 1..12) { "صيغة النسخة الاحتياطية غير مدعومة" }
        val es = root.getJSONArray("employees"); val ms = root.getJSONArray("marks"); val logs = root.optJSONArray("audit") ?: JSONArray()
        val archived = root.optJSONArray("archives") ?: JSONArray()
        val restoredStatuses = root.optJSONArray("statuses") ?: JSONArray()
        val restoredSites = root.optJSONArray("sites")
        val restoredSiteIds = root.optJSONArray("site_identity")
        val restoredAliases = root.optJSONArray("site_aliases")
        val restoredAccounts = root.optJSONArray("accounts")
        val restoredInbox = root.optJSONArray("inbox_events")
        val pendingRestored = root.optJSONArray("sync_outbox")
        val restoreColumns = if(root.optInt("version")>=10) {
            val raw = root.getString("roster_layout")
            require(raw.startsWith("v2|")) { "تنسيق الأعمدة في النسخة غير صحيح" }
            val parsed=RosterColumnLayout.validate(RosterColumnLayout.decode(raw))
            require(RosterColumnLayout.encode(parsed)==raw) { "بيانات هيكل الكشف تالفة" }
            parsed
        } else RosterColumnLayout.defaults
        val restoredWindow = if (root.optInt("version") >= 11) {
            val serialized = root.getString("attendance_window")
            val decoded = AttendanceWindow.parse(serialized)
            require(decoded.serialize() == serialized) { "وقت التحضير في النسخة الاحتياطية غير صالح" }
            decoded
        } else AttendanceWindow()
        val restoredExtras = root.optJSONArray("employee_custom_values") ?: JSONArray()
        if (root.optInt("version")>=10) require(root.has("employee_custom_values")) { "النسخة لا تحتوي قيم الأعمدة" }
        val legalCustom = restoreColumns.filter { it.custom }.mapTo(HashSet()) { it.key }
        val seenExtras=HashSet<Pair<Long,String>>()
        for(i in 0 until restoredExtras.length()) {
            val x=restoredExtras.getJSONObject(i)
            require(x.getString("column_key") in legalCustom && x.getString("value").length<=200 &&
                seenExtras.add(x.getLong("employee_id") to x.getString("column_key"))) { "قيم أعمدة غير متطابقة في النسخة" }
        }

        if (root.optInt("version") >= 12) {
            require(restoredSiteIds != null) { "معرفات المواقع مفقودة" }
            val ids=HashSet<String>(); val names=HashSet<String>()
            for(i in 0 until restoredSiteIds.length()) {
                val e=restoredSiteIds.getJSONObject(i)
                require(runCatching { java.util.UUID.fromString(e.getString("id")) }.isSuccess &&
                    ids.add(e.getString("id")) && names.add(e.getString("name")) && e.getInt("retired") in 0..1) {
                    "معرفات المواقع في النسخة غير صالحة"
                }
            }
        }
        if (root.optInt("version") >= 9) {
            require(pendingRestored != null) { "النسخة لا تحتوي طابور التغييرات" }
            val keys = hashSetOf<String>()
            for(i in 0 until pendingRestored.length()) {
                val change = pendingRestored.getJSONObject(i)
                require(keys.add(change.getString("event_id")) &&
                    change.getString("event_id").matches(Regex("[a-fA-F0-9-]{36}"))) { "معرّف عملية متكرر أو غير صحيح" }
                require(change.getString("role") in listOf("ADMIN","SUPERVISOR"))
                require(change.getString("payload").length <= 1000000)
                JSONObject(change.getString("payload"))
            }
        }
        if (root.optInt("version") >= 5) {
            require(restoredSites != null && restoredAliases != null && restoredAccounts != null) { "النسخة لا تحتوي بيانات المواقع والحسابات" }
            val knownSites = (0 until restoredSites.length()).map { restoredSites.getString(it) }.toSet()
            require(knownSites.size == restoredSites.length() && knownSites.none { it.isBlank() }) { "تكرار أو خطأ بأسماء المواقع" }
            val usernames = hashSetOf<String>()
            var hasAdmin = false
            for (i in 0 until restoredAccounts.length()) {
                val a = restoredAccounts.getJSONObject(i)
                val username = a.getString("username")
                val role = a.getString("role")
                require(username.isNotBlank() && usernames.add(username)) { "اسم حساب مكرر في النسخة" }
                require(a.getString("salt").matches(Regex("[0-9a-fA-F]{32}")) &&
                    a.getString("secret").matches(Regex("[0-9a-fA-F]{64}"))) { "بيانات حماية الحساب غير صالحة" }
                require(role == "ADMIN" || role == "SUPERVISOR") { "نوع الحساب غير صحيح" }
                if (role == "ADMIN") { hasAdmin = true; require(a.isNull("site")) }
                else require(a.getString("site") in knownSites) { "موقع المشرف غير موجود" }
                if (root.optInt("version") >= 7 && !a.isNull("employee_id")) {
                    val linked = a.getLong("employee_id")
                    require(linked > 0L) { "معرف الفرد المرتبط غير صحيح" }
                }
            }
            require(hasAdmin) { "النسخة لا تحتوي حساب إدارة" }
            val aliases = hashSetOf<String>()
            for (i in 0 until restoredAliases.length()) {
                val a = restoredAliases.getJSONObject(i)
                require(aliases.add(a.getString("alias")) && a.getString("current_site") in knownSites) { "أسماء المواقع التاريخية غير متطابقة" }
            }
        }
        if (root.optInt("version") >= 6) {
            require(restoredInbox != null) { "النسخة لا تحتوي سجل الإشعارات" }
            for (i in 0 until restoredInbox.length()) {
                val event = restoredInbox.getJSONObject(i)
                require(event.getString("kind") in listOf("CHANGE","MESSAGE")) { "نوع إشعار غير صحيح" }
                require(event.getString("detail").length <= 100000) { "حجم إشعار غير صالح" }
                require(event.getInt("seen") in 0..1) { "حالة الإشعار غير صحيحة" }
            }
        }
        val restoredCodes = mutableSetOf<String>()
        for (i in 0 until restoredStatuses.length()) {
            val x = restoredStatuses.getJSONObject(i)
            require(restoredCodes.add(x.getString("code"))) { "حالة مكررة في النسخة" }
            require(x.getString("title").isNotBlank() && x.getString("symbol").isNotBlank())
        }
        val allowedCodes = if (restoredStatuses.length() > 0) restoredCodes else Attendance.defaults.map { it.name }.toSet()
        require("PRESENT" in allowedCodes) { "النسخة لا تحتوي حالة الحضور" }
        val since = root.optString("archive_since").takeIf { it.isNotBlank() }
            ?.let { YearMonth.parse(it) }
        for (i in 0 until archived.length()) {
            val x = archived.getJSONObject(i)
            val month = YearMonth.parse(x.getString("month"))
            require(month.isBefore(YearMonth.now())) { "الأرشيف يحتوي شهراً لم ينتهِ بعد" }
            val payload = x.getJSONObject("payload").toString()
            require(ArchiveSnapshot.decode(payload).month == month) { "بيانات شهر الأرشيف غير متطابقة" }
        }
        
        val ids = hashSetOf<Long>(); val codes = hashSetOf<String>(); val rosterNumbers=hashSetOf<String>()
        for(i in 0 until es.length()) {
            val x = es.getJSONObject(i); require(ids.add(x.getLong("id")) && codes.add(x.getString("code"))) { "تكرار بيانات الافراد" }
            val external = x.optString("roster_number", "")
            require(external.isBlank() || rosterNumbers.add(RosterNumbers.clean(external))) { "أرقام (ع) مكررة في النسخة" }
            require(x.getString("name").isNotBlank()); LocalDate.parse(x.getString("started")); if(!x.isNull("ended")) LocalDate.parse(x.getString("ended"))
        }
        for(i in 0 until restoredExtras.length()) {
            require(restoredExtras.getJSONObject(i).getLong("employee_id") in ids) { "قيمة عمود لفرد غير موجود" }
        }
        for(i in 0 until ms.length()) {
            val x=ms.getJSONObject(i); require(x.getLong("employee_id") in ids); LocalDate.parse(x.getString("day")); require(x.getString("status") in allowedCodes)
        }
        if (root.optInt("version") >= 7) {
            val empSite = (0 until es.length()).associate { i ->
                val e = es.getJSONObject(i); e.getLong("id") to e.optString("site", "")
            }
            val occupied = hashSetOf<Long>()
            for (i in 0 until restoredAccounts!!.length()) {
                val a = restoredAccounts.getJSONObject(i)
                if (!a.isNull("employee_id")) {
                    val id = a.getLong("employee_id")
                    require(occupied.add(id) && empSite[id] == a.optString("site")) {
                        "ارتباط المشرف بالفرد أو الموقع غير متطابق في النسخة"
                    }
                }
            }
        }
        
        requireNoPendingEdits()
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.delete("sync_outbox",null,null)
            db.delete("employee_custom_values",null,null)
            db.delete("audit",null,null); db.delete("marks",null,null); db.delete("employees",null,null)
            db.delete("monthly_archives",null,null)
            db.delete("inbox_events",null,null)
            db.delete("attendance_statuses",null,null)
            if (restoredStatuses.length() > 0) {
                for (i in 0 until restoredStatuses.length()) {
                    val x = restoredStatuses.getJSONObject(i)
                    db.execSQL("INSERT INTO attendance_statuses(code,title,symbol,color,position) VALUES(?,?,?,?,?)",
                        arrayOf<Any>(x.getString("code"), x.getString("title"), x.getString("symbol"), x.getLong("color"), x.optInt("position", i)))
                }
            } else createStatuses(db)
            for(i in 0 until es.length()) {
                val x=es.getJSONObject(i)
                val v=ContentValues().apply { put("id",x.getLong("id"));put("code",x.getString("code"));put("roster_number",x.optString("roster_number", ""));put("name",x.getString("name"));put("site",x.optString("site", ""));put("started",x.getString("started")); if(x.isNull("ended")) putNull("ended") else put("ended",x.getString("ended"));put("active", if(x.getBoolean("active")) 1 else 0) }
                db.insertOrThrow("employees",null,v)
            }
            db.execSQL("INSERT OR REPLACE INTO app_meta(name,value) VALUES('roster_layout',?)",
                arrayOf(RosterColumnLayout.encode(restoreColumns)))
            db.execSQL("INSERT OR REPLACE INTO app_meta(name,value) VALUES('attendance_window',?)",
                arrayOf(restoredWindow.serialize()))
            for(i in 0 until restoredExtras.length()) {
                val extra=restoredExtras.getJSONObject(i)
                db.execSQL("INSERT INTO employee_custom_values(employee_id,column_key,value) VALUES(?,?,?)",
                    arrayOf<Any>(extra.getLong("employee_id"),extra.getString("column_key"),extra.getString("value")))
            }
            for(i in 0 until ms.length()) {
                val x=ms.getJSONObject(i)
                db.execSQL("INSERT INTO marks(employee_id,day,status) VALUES(?,?,?)",arrayOf<Any>(x.getLong("employee_id"),x.getString("day"),x.getString("status")))
            }
            for(i in 0 until logs.length()) {
                val x=logs.getJSONObject(i)
                db.execSQL("INSERT INTO audit(employee_id,day,before_state,after_state,changed) VALUES(?,?,?,?,?)",arrayOf<Any>(x.getLong("employee_id"),x.getString("day"),x.getString("before"),x.getString("after"),x.getString("changed")))
            }
            for (i in 0 until archived.length()) {
                val x = archived.getJSONObject(i)
                db.execSQL("INSERT INTO monthly_archives(month,created,payload) VALUES(?,?,?)",
                    arrayOf(x.getString("month"), x.optString("created", ""), x.getJSONObject("payload").toString()))
            }
            setArchiveStart(db, since ?: inferLegacyStart(db, YearMonth.now()))
            if (root.optInt("version") >= 5) {
                db.delete("local_site_aliases", null, null)
                db.delete("site_identity", null, null)
                db.delete("local_users", null, null)
                db.delete("local_sites", null, null)
                for (i in 0 until restoredSites!!.length()) {
                    db.execSQL("INSERT INTO local_sites(name) VALUES(?)", arrayOf(restoredSites.getString(i)))
                }
                if (root.optInt("version") >= 12) {
                    for (i in 0 until restoredSiteIds!!.length()) {
                        val e=restoredSiteIds.getJSONObject(i)
                        db.execSQL("INSERT INTO site_identity(site_id,name,retired) VALUES(?,?,?)",
                            arrayOf<Any>(e.getString("id"),e.getString("name"),e.getInt("retired")))
                    }
                } else SiteIdentityDao.seed(db)
                for (i in 0 until restoredAliases!!.length()) {
                    val a = restoredAliases.getJSONObject(i)
                    db.execSQL("INSERT INTO local_site_aliases(alias,current_site) VALUES(?,?)",
                        arrayOf(a.getString("alias"), a.getString("current_site")))
                }
                for (i in 0 until restoredAccounts!!.length()) {
                    val a = restoredAccounts.getJSONObject(i)
                    db.execSQL("INSERT INTO local_users(username,label,role,site,salt,secret,employee_id,enabled) VALUES(?,?,?,?,?,?,?,?)",
                        arrayOf<Any?>(a.getString("username"), a.getString("label"), a.getString("role"),
                            if (a.isNull("site")) null else a.getString("site"), a.getString("salt"), a.getString("secret"),
                            if (a.isNull("employee_id")) null else a.getLong("employee_id"),
                            if (root.optInt("version") >= 12 && !a.optBoolean("enabled",true)) 0 else 1))
                }
            } else {
                // SERVER: Restore only authorized accounts; old demo credentials are not restored.
                db.execSQL("INSERT OR IGNORE INTO local_sites(name) SELECT DISTINCT trim(site) FROM employees WHERE trim(site)<>''")
                SiteIdentityDao.seed(db)
            }
            removeLegacyDemoCredentials(db)
            ensureTemporaryAdmin(db, resetExisting = true)
            if (root.optInt("version") >= 6) {
                for (i in 0 until restoredInbox!!.length()) {
                    val ev = restoredInbox.getJSONObject(i)
                    db.execSQL("INSERT INTO inbox_events(kind,actor_username,actor_label,recipient_username,site,title,detail,created_at,seen) VALUES(?,?,?,?,?,?,?,?,?)",
                        arrayOf(ev.getString("kind"),ev.getString("actor_username"),ev.getString("actor_label"),
                            ev.getString("recipient_username"),ev.getString("site"),ev.getString("title"),
                            ev.getString("detail"),ev.getString("created_at"),ev.getInt("seen")))
                }
            }
            if (root.optInt("version") >= 9) {
                for (i in 0 until pendingRestored!!.length()) {
                    val ev = pendingRestored.getJSONObject(i)
                    db.execSQL("""INSERT INTO sync_outbox(event_id,actor_username,actor_role,entity,operation,record_key,site,payload,created_at) VALUES(?,?,?,?,?,?,?,?,?)""",
                        arrayOf(ev.getString("event_id"),ev.getString("actor"),ev.getString("role"),
                            ev.getString("entity"),ev.getString("operation"),ev.getString("record_key"),
                            if (ev.isNull("site")) null else ev.getString("site"), ev.getString("payload"), ev.getString("created_at")))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        Attendance.configure(statuses())
    }
}
