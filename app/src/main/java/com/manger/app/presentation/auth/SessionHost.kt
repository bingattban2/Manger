package com.manger.app.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.manger.app.application.AppContainer

  
@Composable
internal fun MangerRoot(container: AppContainer, resumeRevision: Int, lockRevision: Int,
    onExitApp: () -> Unit) {
    val db = container.database
    val rootContext=LocalContext.current
    var session by remember { mutableStateOf<com.manger.app.domain.LocalSession?>(null) }
    var loginExitConfirm by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(lockRevision) {
        if (lockRevision > 0 && session != null) locked = true
    }
    // SERVER: Invalidate the remote session after the authenticated UI has been removed.
    LaunchedEffect(session) {
        if (session == null) db.activate(null)
    }
    BackHandler(enabled = session == null) { loginExitConfirm = true }
    if (session == null) {
        LocalLogin(container.authentication, onLogin = { loggedIn ->
            db.activate(loggedIn)
            
            rootContext.getSharedPreferences("manger_sync_runtime",0).edit()
                .putBoolean("admin_logged_in",loggedIn.isAdmin).apply()

            session = loggedIn
            locked = false
        })
    } else {
        val account = session!!
        Box(Modifier.fillMaxSize()) {
            key(account.username) {
                MangerApp(container, resumeRevision, account, onExitApp = onExitApp, onLogout = {
                    session = null
                    rootContext.getSharedPreferences("manger_sync_runtime",0).edit()
                        .putBoolean("admin_logged_in",false).apply()
                    locked = false
                })
            }
            if (locked) Surface(Modifier.fillMaxSize().clickable { }, color = MaterialTheme.colorScheme.background) {
                // SERVER: Reauthenticate and validate server session and permissions before unlocking.
                LocalLogin(container.authentication, onLogin = { unlockedAccount ->
                    db.activate(unlockedAccount)
                    locked = false
                }, expectedUsername = account.username)
            }
        }
    }
    BackHandler(enabled = locked) { }
    if (session == null && loginExitConfirm) AlertDialog(
        onDismissRequest = { loginExitConfirm = false },
        title = { Text("تأكيد الخروج من التطبيق") }, text = { Text("هل تريد إغلاق التطبيق؟") },
        confirmButton = { TextButton(onClick = { loginExitConfirm = false; onExitApp() }) { Text("خروج") } },
        dismissButton = { TextButton(onClick = { loginExitConfirm = false }) { Text("إلغاء") } }
    )
}

