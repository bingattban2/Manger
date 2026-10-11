package com.manger.app.data.repository

import com.manger.app.data.local.AttendanceDb
import com.manger.app.domain.*
import com.manger.app.domain.repository.RosterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

  
class SqliteRosterRepository(private val database: AttendanceDb) : RosterRepository {
    override suspend fun preview(records: List<ImportedEmployee>): ImportCheck =
        withContext(Dispatchers.IO) { database.checkImport(records) }

    override suspend fun save(records: List<ImportedEmployee>, onProgress: (Int, Int) -> Unit): ImportSummary =
        withContext(Dispatchers.IO) { database.importEmployeesWithSites(records, onProgress) }
}
