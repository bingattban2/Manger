package com.manger.app.data.local

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

  
object DailyBackupScheduler {
    private const val PREFS = "manger_settings"
    private const val LAST = "auto_backup_last"
    private val cutOff = LocalTime.of(22, 0)

    fun start(context: Context) {
        val app = context.applicationContext
        scheduleNext(app)
        if (!LocalTime.now().isBefore(cutOff)) {
            thread(name = "manger-backup-check", isDaemon = true) { backupIfDue(app) }
        }
    }

    private fun scheduleNext(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(cutOff)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, DailyBackupReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, 72122, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    @Synchronized fun backupIfDue(context: Context) {
        if (LocalTime.now().isBefore(cutOff)) return
        val today = LocalDate.now().toString()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getString(LAST, "")?.startsWith(today) == true) return
        runCatching {
            val folder = File(context.filesDir, "daily_backups")
            check(folder.isDirectory || folder.mkdirs()) { "تعذر إنشاء مجلد النسخ" }
            val backup = AttendanceDb(context).use { it.backupForInternalStorage() }
            val target = File(folder, "manger_$today.json")
            val temp = File(folder, "manger_$today.tmp")
            temp.writeText(backup, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            folder.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.name }
                ?.drop(7)?.forEach { it.delete() }
            preferences.edit().putString(LAST, LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).apply()
        }.onFailure { Log.e("MangerAutoBackup", "Automatic recovery snapshot failed", it) }
    }

    internal fun afterAlarm(context: Context) {
        val app = context.applicationContext
        try { backupIfDue(app) } finally { scheduleNext(app) }
    }
}

class DailyBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        thread(name = "manger-nightly-backup") {
            try { DailyBackupScheduler.afterAlarm(context.applicationContext) }
            finally { pending.finish() }
        }
    }
}

  
class BackupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED ->
                DailyBackupScheduler.start(context.applicationContext)
        }
    }
}
