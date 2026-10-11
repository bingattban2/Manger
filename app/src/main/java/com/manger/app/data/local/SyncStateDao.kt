package com.manger.app.data.local

import android.database.sqlite.SQLiteDatabase

internal object SyncStateDao {
    fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_cursors (account TEXT NOT NULL, site_id TEXT NOT NULL DEFAULT '', last_cursor INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account,site_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_receipts (event_id TEXT PRIMARY KEY, acknowledged_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_conflicts (event_id TEXT PRIMARY KEY, reason TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
    }
    fun cursor(db: SQLiteDatabase, username: String, siteId: String?): Long =
        db.rawQuery("SELECT last_cursor FROM sync_cursors WHERE account=? AND site_id=?",arrayOf(username,siteId.orEmpty())).use { c ->
            if(c.moveToFirst()) c.getLong(0) else 0L
        }
    fun advanceCursor(db: SQLiteDatabase, username: String, siteId: String?, cursor: Long) {
        require(cursor>=cursor(db,username,siteId)) { "لا يمكن العودة إلى إصدار أقدم" }
        db.execSQL("INSERT OR REPLACE INTO sync_cursors(account,site_id,last_cursor) VALUES(?,?,?)",
            arrayOf<Any>(username, siteId.orEmpty(), cursor))
    }
    fun noteError(db: SQLiteDatabase,eventId: String,message: String) {
        db.execSQL("UPDATE sync_outbox SET attempts=attempts+1,last_error=? WHERE event_id=?",
            arrayOf(message.take(400),eventId))
        if(message.contains("تعارض") || message.contains("conflict",true)) {
            db.execSQL("INSERT OR REPLACE INTO sync_conflicts(event_id,reason) VALUES(?,?)",arrayOf(eventId,message.take(400)))
        }
    }
}
