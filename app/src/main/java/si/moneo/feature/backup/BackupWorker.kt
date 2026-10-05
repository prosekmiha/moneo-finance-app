package si.moneo.feature.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import si.moneo.MoneoApp

/** Tedenska samodejna varnostna kopija v mapo, ki jo je uporabnik izbral v nastavitvah. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneoApp
        val folder = app.container.prefs.backupFolder ?: return Result.success()
        return try {
            app.container.backup.writeToFolder(folder)
            app.container.prefs.lastBackupAt = System.currentTimeMillis()
            Result.success()
        } catch (e: SecurityException) {
            // Dostop do mape je bil preklican - izklopi, da ne poskušamo v nedogled
            app.container.prefs.backupFolder = null
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "auto_backup_weekly"
    }
}
