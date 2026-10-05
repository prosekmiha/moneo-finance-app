package si.moneo.feature.debts

import si.moneo.ui.str
import si.moneo.ui.fmtDayMonth
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
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.DebtEntity
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import java.time.LocalDate

/** Dnevni worker: na dan roka vračila opomni na še neporavnan dolg. */
class DebtReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneoApp
        val prefs = app.container.prefs
        val today = LocalDate.now()
        // Na dan roka ali prvič po njem (če worker tisti dan ni tekel), za vsak rok le enkrat
        app.container.repository.openDebtsWithDueDate()
            .filter { !millisToLocalDate(it.dueDate!!).isAfter(today) }
            .forEach { debt ->
                val key = "debt:${debt.uid}:${debt.dueDate}"
                if (!prefs.wasReminded(key)) {
                    notify(debt, millisToLocalDate(debt.dueDate!!), today)
                    prefs.markReminded(key, today)
                }
            }
        return Result.success()
    }

    private fun notify(debt: DebtEntity, due: LocalDate, today: LocalDate) {
        // En id na dolg: ponovni zagon isti dan obvestilo le zamenja
        val id = ("debt:" + debt.uid).hashCode()
        val intent = PendingIntent.getActivity(
            applicationContext, id,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val amount = formatCents(debt.remainingCents)
        val deadline = if (due == today) str(R.string.debt_due_today) else str(R.string.debt_due_was, due.fmtDayMonth())
        val (title, text) = if (debt.direction == DebtDirection.LENT) {
            str(R.string.debt_lent_title, debt.person, amount) to str(R.string.debt_lent_text, deadline)
        } else {
            str(R.string.debt_borrowed_title, amount, debt.person) to deadline
        }
        val notification = NotificationCompat.Builder(applicationContext, MoneoApp.CHANNEL_DEBTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
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
        const val WORK_NAME = "debt_reminders_daily"
    }
}
