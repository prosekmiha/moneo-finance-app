package si.moneo.feature.reminder

import si.moneo.ui.str
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import si.moneo.MainActivity
import si.moneo.MoneoApp
import si.moneo.R
import si.moneo.data.prefs.AppPrefs
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Večerni opomnik ob izbrani uri: če tisti dan ni bilo nobenega ročnega vnosa,
 * pošlje obvestilo "Si danes kaj zapravil?". Po vsakem zagonu se naroči za naslednji dan.
 */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneoApp
        val prefs = app.container.prefs
        if (!prefs.dailyReminder) return Result.success()

        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (!app.container.repository.hasManualEntrySince(startOfDay)) notifyUser()

        // Pripni naslednji termin (REPLACE bi preklical ta, še tekoči zagon)
        schedule(applicationContext, prefs, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    private fun notifyUser() {
        val intent = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, MainActivity.intent(applicationContext, "expense"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, MoneoApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.notif_reminder_title))
            .setContentText(str(R.string.notif_reminder_text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS ni odobren - preskoči obvestilo
        }
    }

    companion object {
        private const val WORK_NAME = "daily_reminder"
        private const val NOTIFICATION_ID = 4711

        /**
         * Naroči naslednji opomnik ob izbrani uri (ali ga prekliče, če je izklopljen).
         * KEEP ob zagonu aplikacije (obstoječi termin ostane), REPLACE ob spremembi nastavitev.
         */
        fun schedule(context: Context, prefs: AppPrefs, policy: ExistingWorkPolicy) {
            val wm = WorkManager.getInstance(context)
            if (!prefs.dailyReminder) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val time = LocalTime.of(prefs.dailyReminderMinutes / 60, prefs.dailyReminderMinutes % 60)
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            wm.enqueueUniqueWork(
                WORK_NAME,
                policy,
                OneTimeWorkRequestBuilder<DailyReminderWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }
    }
}
