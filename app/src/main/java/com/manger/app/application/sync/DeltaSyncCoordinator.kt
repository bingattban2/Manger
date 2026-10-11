package com.manger.app.application.sync

import com.manger.app.data.local.SyncPendingEvent
import com.manger.app.data.remote.RemoteChange
import com.manger.app.data.remote.RemoteDeltaGateway

// SERVER: Durable queue ACK occurs only after an authenticated, matching server receipt.
interface ChangeStore {
    suspend fun pending(limit: Int): List<SyncPendingEvent>
    suspend fun acknowledge(eventId: String)
    suspend fun noteFailure(eventId: String, description: String)
    suspend fun cursor(): Long
    suspend fun applyInbound(change: RemoteChange)
    suspend fun persistCursor(cursor: Long)
}

data class SyncOutcome(val uploaded: Int, val downloaded: Int, val pending: Boolean, val error: String?)

class DeltaSyncCoordinator(private val store: ChangeStore, private val remote: RemoteDeltaGateway) {
    suspend fun synchronize(): SyncOutcome {
        var uploads = 0
        var downloads = 0
        try {
            val events = store.pending(100)
            for (event in events) {
                val accepted = try {
                    if (event.role == "SUPERVISOR") {
                        require(event.entity == "attendance")
                        remote.pushAttendance(event)
                    } else if (event.entity == "attendance") {
                        remote.pushAttendance(event)
                    } else remote.pushAdmin(event)
                } catch (e: Exception) {
                    store.noteFailure(event.eventId,e.message.orEmpty())
                    throw e
                }
                if (!accepted) error("السيرفر لم يؤكد العملية ${event.eventId}")
                store.acknowledge(event.eventId)
                uploads++
            }
            // SERVER: Incoming changes are applied to local authorized rows BEFORE advancing the cursor.
            while (true) {
                val from = store.cursor()
                val batch = remote.pull(from,200)
                if(batch.isEmpty()) break
                for (change in batch) {
                    require(change.cursor > store.cursor()) { "ترتيب أحداث السيرفر غير صحيح" }
                    store.applyInbound(change)
                    store.persistCursor(change.cursor)
                    downloads++
                }
                if (batch.size < 200) break
            }
            return SyncOutcome(uploads,downloads,events.size==100,null)
        } catch (e: Exception) {
            return SyncOutcome(uploads,downloads,true,e.message ?: "فشل الاتصال بالسيرفر")
        }
    }
}
