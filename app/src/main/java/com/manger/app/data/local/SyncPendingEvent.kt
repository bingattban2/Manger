package com.manger.app.data.local

  
data class SyncPendingEvent(
    val eventId: String, val actor: String, val role: String, val entity: String,
    val operation: String, val recordKey: String, val site: String?, val payload: String
)
