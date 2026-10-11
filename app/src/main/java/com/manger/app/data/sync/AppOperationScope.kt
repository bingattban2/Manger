package com.manger.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppOperationScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
