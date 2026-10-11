package com.manger.app.application.roster

import com.manger.app.domain.ImportCheck
import com.manger.app.domain.ImportSummary
import com.manger.app.domain.ImportedEmployee
import com.manger.app.domain.repository.RosterRepository
import com.manger.app.domain.repository.RosterSheetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

  
class ImportRosterUseCase(private val repository: RosterRepository, private val reader: RosterSheetReader) {
    suspend fun read(input: InputStream, filename: String, progress: (Int, Int) -> Unit): Map<String, List<ImportedEmployee>> =
        withContext(Dispatchers.IO) { input.use { reader.read(it, filename, progress) } }

    suspend fun preview(rows: List<ImportedEmployee>): ImportCheck = repository.preview(rows)

    suspend fun commit(rows: List<ImportedEmployee>, progress: (Int, Int) -> Unit): ImportSummary =
        repository.save(rows, progress)
}
