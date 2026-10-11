package com.manger.app.data.local

import android.database.sqlite.SQLiteDatabase
import com.manger.app.domain.LocalSession
import org.json.JSONObject
import java.util.UUID

  
internal object SyncOutboxDao {
    fun createSchema(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS sync_outbox (
            event_id TEXT PRIMARY KEY NOT NULL,
            actor_username TEXT NOT NULL,
            actor_role TEXT NOT NULL,
            entity TEXT NOT NULL,
            operation TEXT NOT NULL,
            record_key TEXT NOT NULL,
            site TEXT,
            payload TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_error TEXT
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_outbox_created ON sync_outbox(created_at)")
    }

    fun enqueue(db: SQLiteDatabase, actor: LocalSession, entity: String, operation: String,
                recordKey: String, site: String?, payload: JSONObject) {
        check(actor.isAdmin || entity == "attendance") { "تغيير غير مسموح به للمشرف" }
        db.execSQL("""INSERT INTO sync_outbox(
            event_id, actor_username, actor_role, entity, operation, record_key, site, payload
        ) VALUES(?,?,?,?,?,?,?,?)""", arrayOf<Any?>(
            UUID.randomUUID().toString(), actor.username,
            if (actor.isAdmin) "ADMIN" else "SUPERVISOR",
            entity, operation, recordKey, site, payload.toString()
        ))
    }

    fun pendingCount(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT COUNT(*) FROM sync_outbox", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun pendingEvents(db: SQLiteDatabase, limit: Int): List<SyncPendingEvent> =
        db.rawQuery(
            "SELECT event_id,actor_username,actor_role,entity,operation,record_key,site,payload FROM sync_outbox ORDER BY rowid LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        ).use { c -> buildList {
            while (c.moveToNext()) add(SyncPendingEvent(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getString(5), c.getString(6), c.getString(7)
            ))
        } }

      
    fun acknowledge(db: SQLiteDatabase, eventId: String) {
        require(eventId.matches(Regex("[a-fA-F0-9-]{36}")))
        db.beginTransaction()
        try {
            val deleted = db.delete("sync_outbox", "event_id=?", arrayOf(eventId))
            require(deleted == 1) { "تعذر تأكيد العملية: غير موجودة في قائمة الانتظار" }
            db.execSQL("INSERT OR IGNORE INTO sync_receipts(event_id) VALUES(?)",arrayOf(eventId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
