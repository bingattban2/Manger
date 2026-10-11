package com.manger.app.domain.repository

import com.manger.app.domain.ImportCheck
import com.manger.app.domain.ImportSummary
import com.manger.app.domain.ImportedEmployee
import java.io.InputStream

interface RosterRepository {
    suspend fun preview(records: List<ImportedEmployee>): ImportCheck
    suspend fun save(records: List<ImportedEmployee>, onProgress: (Int, Int) -> Unit): ImportSummary
}

interface RosterSheetReader {
    fun read(stream: InputStream, fileName: String, onProgress: (Int, Int) -> Unit): Map<String, List<ImportedEmployee>>
}
