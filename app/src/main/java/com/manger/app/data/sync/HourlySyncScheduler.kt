package com.manger.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

  
object HourlySyncScheduler {
    fun triggerAfterLocalChange(context: Context) {
        // SERVER: The outbox must retain events until an acknowledged server commit.
        if (!SyncBackend.isConfigured) return
        val request = OneTimeWorkRequestBuilder<HourlySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
    fun start(context: Context) {
        // SERVER: Register periodic delta sync only after authentication and secure endpoint configuration.
        if (!SyncBackend.isConfigured) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<HourlySyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "manger-hourly-incremental-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class HourlySyncWorker(context: Context, params: WorkerParameters) : Worker(context,params) {
    override fun doWork(): Result {
        
        if (!SyncBackend.isConfigured) return Result.success()
        val runtime = applicationContext.getSharedPreferences("manger_sync_runtime",Context.MODE_PRIVATE)
        
        
        if (runtime.getBoolean("app_foreground",false) && runtime.getBoolean("admin_logged_in",false))
            return Result.success()
        // SERVER: Pull only authorized site deltas, use a revision cursor and validate acknowledgements.
        return SyncBackend.syncBackground(applicationContext)
    }
}

  
object SyncBackend {
    // SERVER: Set only when project URL, publishable key, verified Auth session, RLS and delta RPC are connected.
    val isConfigured: Boolean = false
    fun syncBackground(context: Context): ListenableWorker.Result = ListenableWorker.Result.failure()
    fun explain(): String = "لم يُربط هذا الإصدار بخادم Supabase بعد. تُحفظ التعديلات محلياً في طابور دائم دون حذفها أو الادعاء برفعها."
}
