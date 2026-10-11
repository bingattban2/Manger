package com.manger.app.application

import com.manger.app.application.roster.ImportRosterUseCase
import com.manger.app.domain.*
import com.manger.app.domain.repository.RosterRepository
import com.manger.app.domain.repository.RosterSheetReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class ImportRosterUseCaseTest {
    private val duplicateNames = listOf(
        ImportedEmployee("محمد سالم", "موقع 1", "010"),
        ImportedEmployee("محمد سالم", "موقع 1", "011")
    )
    @Test fun `same names and different roster numbers are valid`() = runBlocking {
        val repo = object : RosterRepository {
            override suspend fun preview(records: List<ImportedEmployee>): ImportCheck =
                RosterImportValidator.inspect(records, emptyList())
            override suspend fun save(records: List<ImportedEmployee>, onProgress: (Int, Int) -> Unit): ImportSummary {
                onProgress(records.size, records.size)
                return ImportSummary(records.size, 0)
            }
        }
        val reader = object : RosterSheetReader {
            override fun read(stream: InputStream, fileName: String, onProgress: (Int, Int) -> Unit): Map<String, List<ImportedEmployee>> =
                mapOf("Sheet1" to duplicateNames)
        }
        val useCase = ImportRosterUseCase(repo, reader)
        val items = useCase.read(byteArrayOf().inputStream(), "list.xlsx") { _, _ -> }.values.flatten()
        assertEquals(2, useCase.preview(items).added)
        assertEquals(2, useCase.commit(items) { _, _ -> }.added)
        assertTrue(runCatching { useCase.preview(items + items[0]) }.isFailure)
    }
}
