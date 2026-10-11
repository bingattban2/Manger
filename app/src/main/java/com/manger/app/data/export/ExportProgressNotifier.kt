package com.manger.app.data.export

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

  
class ExportProgressNotifier(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channel = "manger_roster_exports"
    private val notificationId = 17021
    private fun enabled(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(completed: Int, total: Int, name: String) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(NotificationChannel(channel, "تصدير الكشوف", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("تصدير كشوف القوة ($completed/$total)")
            .setContentText(name)
            .setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(total.coerceAtLeast(1), completed, false)
            .build()
        manager.notify(notificationId, notification)
    }
    fun finish(message: String, successful: Boolean) {
        if (!enabled()) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(if (successful) "اكتمل التصدير" else "توقف تصدير الكشوف")
            .setContentText(message).setOngoing(false).setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }
}
