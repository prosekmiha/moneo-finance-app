package si.moneo.feature.subscriptions

import si.moneo.ui.str
import si.moneo.ui.qty
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import si.moneo.MainActivity
import si.moneo.MoneoApp
import si.moneo.R
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.domain.subscriptions.hasEnded
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Dnevni worker za naročnine:
 *  - pošlje opomnik N dni pred plačilom (če ga ima naročnina nastavljenega)
 *  - zapadla plačila zapiše kot odhodke na datum plačila
 */
class SubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneoApp
        val repo = app.container.repository
        val prefs = app.container.prefs
        val today = LocalDate.now()

        // Opomniki pred knjiženjem - knjiženje premakne datum naslednjega plačila naprej.
        // Kadar koli v oknu "N dni prej .. dan plačila" (če worker kak dan ne teče), a le enkrat na plačilo.
        for (sub in repo.activeSubscriptions()) {
            val days = sub.remindDaysBefore ?: continue
            if (sub.hasEnded) continue
            val until = ChronoUnit.DAYS.between(today, millisToLocalDate(sub.nextPaymentDate))
            val key = "subscription:${sub.uid}:${sub.nextPaymentDate}"
            if (until in 0..days.toLong() && !prefs.wasReminded(key)) {
                notify(sub, until)
                prefs.markReminded(key, today)
            }
        }
        repo.chargeDueSubscriptions(today)
        // Isti dnevni zagon zapiše tudi samodejna mesečna vplačila v cilje
        repo.applyAutoSavings(today)
        return Result.success()
    }

    private fun notify(sub: SubscriptionEntity, daysUntil: Long) {
        // En id na naročnino: ponovni zagon workerja isti dan obvestilo le zamenja
        val id = ("subscription:" + sub.uid).hashCode()
        val intent = PendingIntent.getActivity(
            applicationContext, id,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val `when` = when (daysUntil) {
            0L -> str(R.string.relative_today)
            1L -> str(R.string.relative_tomorrow)
            2L -> str(R.string.relative_day_after)
            else -> qty(R.plurals.in_days, daysUntil.toInt(), daysUntil.toInt())
        }
        val notification = NotificationCompat.Builder(applicationContext, MoneoApp.CHANNEL_SUBSCRIPTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.notif_sub_title, sub.title, `when`))
            .setContentText(str(R.string.notif_sub_text, formatCents(sub.amountCents)))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS ni odobren - preskoči obvestilo
        }
    }

    companion object {
        const val WORK_NAME = "subscriptions_daily"
    }
}
