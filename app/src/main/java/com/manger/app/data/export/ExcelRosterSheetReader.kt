package com.manger.app.data.export

import com.manger.app.domain.ImportedEmployee
import com.manger.app.domain.repository.RosterSheetReader
import java.io.InputStream

  
class ExcelRosterSheetReader : RosterSheetReader {
    override fun read(stream: InputStream, fileName: String,
        onProgress: (Int, Int) -> Unit): Map<String, List<ImportedEmployee>> =
        Spreadsheet.readEmployeeSheets(stream, fileName, onProgress)
}
