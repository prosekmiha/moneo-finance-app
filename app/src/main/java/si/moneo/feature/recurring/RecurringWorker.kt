package si.moneo.feature.recurring

import si.moneo.ui.str
import si.moneo.ui.qty
import si.moneo.ui.fmtDayMonth
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.moneo.MainActivity
import si.moneo.MoneoApp
import si.moneo.R
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.repo.FinanceRepository
import si.moneo.ui.formatCents

/**
 * Dnevni worker za ponavljajoča pravila (enako se zažene tudi ob odprtju aplikacije):
 *  - zapadle termine zapiše kot transakcije na datum termina (vse zamujene naenkrat)
 *  - samodejni zapis -> potrjeno; sicer nepotrjeno + obvestilo z gumbom "Potrdi"
 */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        processAndNotify(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "recurring_rules_daily"

        /** Zapiše zapadle termine in za nepotrjene pošlje obvestilo. */
        suspend fun processAndNotify(context: Context) {
            val repo = (context.applicationContext as MoneoApp).container.repository
            repo.processDueRecurringRules().forEach { notifyDue(context, it) }
        }

        private fun notifyDue(context: Context, due: FinanceRepository.DueRule) {
            val rule = due.rule
            val id = ("recurring:" + rule.uid).hashCode()
            val uids = due.dates.map { FinanceRepository.recurringTxUid(rule.uid, it) }.toTypedArray()
            val amount = (if (rule.type == TransactionType.INCOME) "+" else "") + formatCents(rule.amountCents)
            val dates = due.dates.joinToString(", ") { it.fmtDayMonth() }
            val open = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val confirm = PendingIntent.getBroadcast(
                context, id,
                Intent(context, ConfirmRecurringReceiver::class.java)
                    .putExtra(ConfirmRecurringReceiver.EXTRA_UIDS, uids)
                    .putExtra(ConfirmRecurringReceiver.EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, MoneoApp.CHANNEL_RECURRING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(str(R.string.notif_confirm_title, rule.title, amount))
                .setContentText(
                    if (due.dates.size == 1) str(R.string.notif_due_one, dates)
                    else str(R.string.notif_due_many, qty(R.plurals.due_terms, due.dates.size, due.dates.size), dates),
                )
                .setContentIntent(open)
                .addAction(0, str(R.string.confirm), confirm)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS ni odobren - transakcija vseeno čaka na Domov
            }
        }
    }
}

/** Gumb "Potrdi" v obvestilu: potrdi nepotrjene transakcije ponavljajočega pravila. */
class ConfirmRecurringReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uids = intent.getStringArrayExtra(EXTRA_UIDS) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                val repo = (context.applicationContext as MoneoApp).container.repository
                uids.forEach { repo.confirmTransaction(it) }
                NotificationManagerCompat.from(context).cancel(notificationId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext, str(R.string.confirmed_toast), Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_UIDS = "uids"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
