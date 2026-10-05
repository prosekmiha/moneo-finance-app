package si.moneo.di

import android.content.Context
import si.moneo.data.backup.BackupManager
import si.moneo.data.db.AppDatabase
import si.moneo.data.prefs.AppPrefs
import si.moneo.data.repo.FinanceRepository

/** Ročni DI - preprosto, brez odvisnosti od Hilta/Koina. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val repository: FinanceRepository = FinanceRepository(database)
    val backup: BackupManager = BackupManager(context.applicationContext, database)
    val prefs: AppPrefs = AppPrefs(context)
}
