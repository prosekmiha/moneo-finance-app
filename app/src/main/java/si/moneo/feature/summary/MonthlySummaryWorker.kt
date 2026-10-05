package si.moneo.feature.summary

import si.moneo.ui.str
import si.moneo.ui.fmt
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
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.formatCents
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dnevni worker: prvi dan po koncu meseca pošlje povzetek prejšnjega meseca
 * (poraba, primerjava s predprejšnjim mesecem, največja kategorija). Pošlje ga le enkrat na mesec.
 */
class MonthlySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneoApp
        val prefs = app.container.prefs
        if (!prefs.monthlySummary) return Result.success()

        val month = YearMonth.now().minusMonths(1)
        if (prefs.lastSummaryMonth == month.toString()) return Result.success()

        val repo = app.container.repository
        val txs = repo.transactionsForMonth(month).filter { it.confirmed }
        val prevTxs = repo.transactionsForMonth(month.minusMonths(1)).filter { it.confirmed }
        if (txs.isEmpty()) {
            prefs.lastSummaryMonth = month.toString()
            return Result.success()
        }

        val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
        val prevExpense = prevTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        val topCategory = txs.filter { it.type == TransactionType.EXPENSE && it.categoryUid != null }
            .groupBy { it.categoryUid }.maxByOrNull { (_, l) -> l.sumOf { it.amountCents } }
            ?.key?.let { repo.categoryByUid(it)?.title }

        val monthName = month.atDay(1).fmt(si.moneo.R.string.fmt_month_name, "LLLL")
        val delta = if (prevExpense > 0) (((expense - prevExpense).toDouble() / prevExpense) * 100).roundToInt() else null
        val text = buildString {
            append(str(R.string.notif_summary_spent, formatCents(expense)))
            delta?.let { append(str(R.string.notif_summary_delta, (if (it > 0) "+" else "") + str(R.string.percent, it))) }
            append(str(R.string.notif_summary_received, formatCents(income)))
            topCategory?.let { append(str(R.string.notif_summary_top, it)) }
        }
        notify(str(R.string.notif_summary_title, monthName), text)
        prefs.lastSummaryMonth = month.toString()
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val intent = PendingIntent.getActivity(
            applicationContext, 7001,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, MoneoApp.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(7001, notification)
        } catch (_: SecurityException) {
            // Obvestila niso dovoljena
        }
    }

    companion object {
        const val WORK_NAME = "monthly_summary_daily"
    }
}
