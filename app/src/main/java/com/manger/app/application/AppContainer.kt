package com.manger.app.application

import android.content.Context
import com.manger.app.application.auth.SignInUseCase
import com.manger.app.application.roster.ImportRosterUseCase
import com.manger.app.data.export.ExcelRosterSheetReader
import com.manger.app.data.local.AttendanceDb
import com.manger.app.data.repository.SqliteAuthenticationRepository
import com.manger.app.data.repository.SqliteDashboardRepository
import com.manger.app.data.repository.SqliteRosterRepository
import com.manger.app.domain.repository.DashboardRepository

  
class AppContainer(context: Context) {
    val database = AttendanceDb(context.applicationContext)
    // SERVER: Replace local authentication with the Supabase Auth repository only after verified RLS and account provisioning.
    val authentication = SignInUseCase(SqliteAuthenticationRepository(database))
    val dashboard: DashboardRepository = SqliteDashboardRepository(database)
    val rosterImport = ImportRosterUseCase(SqliteRosterRepository(database), ExcelRosterSheetReader())
}
