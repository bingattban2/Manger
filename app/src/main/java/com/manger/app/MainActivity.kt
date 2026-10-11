package com.manger.app

import android.os.Bundle
import android.content.Context
import java.util.concurrent.TimeUnit
import androidx.compose.material3.MaterialTheme
import com.manger.app.presentation.MangerTypography
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import com.manger.app.application.AppContainer
import com.manger.app.data.local.DailyBackupScheduler
import com.manger.app.domain.Attendance
import com.manger.app.presentation.MangerRoot

  
class MainActivity : ComponentActivity() {
    private val resumeRevision = mutableIntStateOf(0)
    private val lockRevision = mutableIntStateOf(0)
    private var startedOnce = false

    override fun onStart() {
        super.onStart()
        getSharedPreferences("manger_sync_runtime", Context.MODE_PRIVATE).edit()
            .putBoolean("app_foreground",true).apply()
        val state = getSharedPreferences("manger_lock", Context.MODE_PRIVATE)
        val stoppedAt = state.getLong("last_stopped", 0L)
        if (startedOnce && stoppedAt > 0L &&
            System.currentTimeMillis() - stoppedAt >= TimeUnit.MINUTES.toMillis(10)) {
            
            lockRevision.intValue++
        }
        startedOnce = true
    }

    override fun onStop() {
        getSharedPreferences("manger_sync_runtime", Context.MODE_PRIVATE).edit()
            .putBoolean("app_foreground",false).apply()
        getSharedPreferences("manger_lock", Context.MODE_PRIVATE).edit()
            .putLong("last_stopped", System.currentTimeMillis()).commit()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumeRevision.intValue++
        DailyBackupScheduler.start(applicationContext)
        com.manger.app.data.sync.HourlySyncScheduler.start(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(applicationContext)
        Attendance.configure(container.database.statuses())
        setContent { MaterialTheme(typography=MangerTypography) {
            MangerRoot(container, resumeRevision.intValue, lockRevision.intValue,
                onExitApp = { finish() })
        } }
    }
}
