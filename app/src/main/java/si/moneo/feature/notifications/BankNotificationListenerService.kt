package si.moneo.feature.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import si.moneo.MoneoApp
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.notifications.BankNotificationParser

/**
 * Poslušalec obvestil: zazna bančna obvestila o transakcijah (SMS aplikacije,
 * mobilne banke) in ustvari NEpotrjeno transakcijo, ki jo uporabnik kasneje
 * potrdi z enim tapom na domačem zaslonu.
 *
 * Razčlenjevanje je v [BankNotificationParser].
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == this.packageName) return
        val n = sbn.notification
        // Trajna obvestila (predvajanje, navigacija) in povzetki skupin niso transakcije
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val parsed = BankNotificationParser.parse(title, text) ?: return
        if (isDuplicate(packageName, parsed.amountCents, sbn.postTime)) return

        val app = application as MoneoApp
        scope.launch {
            val type = if (parsed.isIncome) TransactionType.INCOME else TransactionType.EXPENSE
            // Predlog kategorije iz imena trgovca: pretekle transakcije, sicer ključne besede kategorij
            val categoryUid = app.container.repository.suggestCategoryForComment(parsed.merchant, type)
            app.container.repository.saveTransaction(
                TransactionEntity(
                    type = type,
                    amountCents = parsed.amountCents,
                    date = sbn.postTime,
                    comment = parsed.merchant.ifBlank { title.take(60) },
                    categoryUid = categoryUid,
                    source = TransactionSource.NOTIFICATION,
                    confirmed = false, // uporabnik potrdi na domačem zaslonu
                )
            )
        }
    }

    /** Banke obvestilo pogosto posodobijo ali pošljejo SMS + push - isti znesek iz iste aplikacije v 3 min ignoriramo. */
    private fun isDuplicate(pkg: String, cents: Long, time: Long): Boolean = synchronized(recent) {
        recent.removeAll { time - it.third > DEDUP_WINDOW_MS }
        val dup = recent.any { it.first == pkg && it.second == cents }
        if (!dup) recent += Triple(pkg, cents, time)
        dup
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 3 * 60 * 1000L
        private val recent = mutableListOf<Triple<String, Long, Long>>()
    }
}
