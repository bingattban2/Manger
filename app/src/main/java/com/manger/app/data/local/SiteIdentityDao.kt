package com.manger.app.data.local

import android.database.sqlite.SQLiteDatabase
import java.util.UUID

internal object SiteIdentityDao {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS site_identity (site_id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, retired INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_site_identity_live ON site_identity(retired,name)")
        seed(db)
        db.execSQL("ALTER TABLE local_users ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
    }
    fun seed(db: SQLiteDatabase) {
        db.rawQuery("SELECT name FROM local_sites UNION SELECT DISTINCT trim(site) FROM employees WHERE trim(site) <> ''",null).use { c ->
            while (c.moveToNext()) add(db,c.getString(0))
        }
    }
    fun add(db: SQLiteDatabase, name: String) {
        if (findId(db, name) != null) return
        // SERVER: Reactivating a retired site preserves its durable identity.
        val retiredId = db.rawQuery(
            "SELECT site_id FROM site_identity WHERE name=? AND retired=1 LIMIT 1", arrayOf(name)
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        if (retiredId != null) {
            db.execSQL("UPDATE site_identity SET retired=0 WHERE site_id=?", arrayOf(retiredId))
        } else {
            db.execSQL("INSERT INTO site_identity(site_id,name) VALUES(?,?)",
                arrayOf(UUID.randomUUID().toString(), name))
        }
    }
    fun rename(db: SQLiteDatabase, oldName: String, newName: String) {
        require(findId(db,oldName)!=null) { "تعذر العثور على معرف الموقع القديم" }
        db.execSQL("UPDATE site_identity SET name=? WHERE name=? AND retired=0",arrayOf(newName,oldName))
    }
    fun retire(db: SQLiteDatabase, name: String) {
        db.execSQL("UPDATE site_identity SET retired=1 WHERE name=?",arrayOf(name))
    }
    fun findId(db: SQLiteDatabase, name: String): String? =
        db.rawQuery("SELECT site_id FROM site_identity WHERE name=? AND retired=0",arrayOf(name)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    fun findName(db: SQLiteDatabase, id: String): String? =
        db.rawQuery("SELECT name FROM site_identity WHERE site_id=? AND retired=0",arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
