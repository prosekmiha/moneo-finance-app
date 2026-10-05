package si.moneo.feature.widget

import si.moneo.ui.str
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import si.moneo.MainActivity
import si.moneo.MoneoApp
import si.moneo.R
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import java.time.LocalDate
import java.time.YearMonth

/**
 * Velik widget s priljubljenimi vnosi. Vsak gumb v mreži z enim tapom zapiše
 * transakcijo z današnjim datumom (brez odpiranja aplikacije) in pokaže potrditev.
 */
class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                update(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ADD -> add(context, intent.getStringExtra(EXTRA_FAVORITE_UID) ?: return)
            ACTION_UNDO -> undo(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun undo(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as MoneoApp
                val prefs = app.container.prefs
                val txUid = prefs.widgetLastAddUid
                if (txUid != null && isUndoable(prefs.widgetLastAddAt)) {
                    app.container.repository.deleteTransaction(txUid)
                    toast(context, str(R.string.undone_named, prefs.widgetLastAddLabel.orEmpty()))
                }
                prefs.widgetLastAddUid = null
                refresh(context)
                QuickAddWidgetProvider.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun add(context: Context, uid: String) {
        val pending = goAsync()
        scope.launch {
            try {
                val repo = (context.applicationContext as MoneoApp).container.repository
                val favorite = repo.favoriteByUid(uid)?.takeIf { !it.deleted }
                if (favorite == null) {
                    toast(context, str(R.string.favorite_missing))
                } else {
                    val txUid = repo.addFromFavorite(favorite)
                    val label = "${favorite.title} ${formatCents(favorite.amountCents)}"
                    toast(context, str(R.string.added_named, label))
                    // Zapomni si vnos, da ga lahko v widgetu razveljavimo; po UNDO_WINDOW_MS se gumb skrije
                    (context.applicationContext as MoneoApp).container.prefs.apply {
                        widgetLastAddUid = txUid
                        widgetLastAddLabel = label
                        widgetLastAddAt = System.currentTimeMillis()
                    }
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        UNDO_EXPIRY_WORK, ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<WidgetUndoExpiryWorker>()
                            .setInitialDelay(UNDO_WINDOW_MS, TimeUnit.MILLISECONDS).build(),
                    )
                    refresh(context)
                    QuickAddWidgetProvider.refresh(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_ADD = "si.moneo.widget.ADD_FAVORITE"
        private const val ACTION_UNDO = "si.moneo.widget.UNDO_FAVORITE"
        private const val UNDO_EXPIRY_WORK = "favorites_widget_undo_expiry"
        const val UNDO_WINDOW_MS = 60_000L

        private fun isUndoable(addedAt: Long) = System.currentTimeMillis() - addedAt < UNDO_WINDOW_MS
        const val EXTRA_FAVORITE_UID = "favorite_uid"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Osveži glavo in gumbe vseh primerkov (ob spremembi priljubljenih, transakcij ali teme). */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FavoritesWidgetProvider::class.java))
            if (ids.isEmpty()) return
            update(context, manager, ids)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.fav_grid)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as MoneoApp
            val today = LocalDate.now()
            val todaySpent = app.container.repository.transactionsForMonth(YearMonth.now())
                .filter { it.confirmed && it.type == TransactionType.EXPENSE && millisToLocalDate(it.date) == today }
                .sumOf { it.amountCents }
            val prefs = app.container.prefs
            val hidden = prefs.hideBalance
            val c = widgetColors(context)
            val undoLabel = prefs.widgetLastAddLabel.takeIf { prefs.widgetLastAddUid != null && isUndoable(prefs.widgetLastAddAt) }

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_favorites)
                // Besedila iz kode: zaganjalnik bi postavitev sicer razrešil v jeziku sistema
                views.setTextViewText(R.id.fav_title, str(R.string.widget_favorites_title))
                views.setTextViewText(R.id.fav_undo_text, str(R.string.widget_undo))
                views.setTextViewText(R.id.fav_empty, str(R.string.widget_favorites_empty))
                views.setContentDescription(R.id.fav_add, str(R.string.shortcut_expense))
                views.tint(R.id.fav_bg, c.background)
                views.setTextColor(R.id.fav_title, c.text)
                views.setTextColor(R.id.fav_sub, c.subtext)
                views.setTextColor(R.id.fav_empty, c.subtext)
                views.tint(R.id.fav_add_bg, c.accent)
                views.tint(R.id.fav_add_icon, c.onAccent)
                views.setTextViewText(
                    R.id.fav_sub,
                    when {
                        undoLabel != null -> str(R.string.added_named, undoLabel)
                        hidden || todaySpent == 0L -> str(R.string.widget_favorites_hint)
                        else -> str(R.string.spent_today, formatCents(todaySpent))
                    },
                )
                views.setViewVisibility(R.id.fav_undo, if (undoLabel != null) View.VISIBLE else View.GONE)
                views.tint(R.id.fav_undo_bg, c.accentBg)
                views.setTextColor(R.id.fav_undo_text, c.onAccentBg)
                views.setOnClickPendingIntent(
                    R.id.fav_undo,
                    PendingIntent.getBroadcast(
                        context, 12, Intent(context, FavoritesWidgetProvider::class.java).setAction(ACTION_UNDO),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )

                // Mreža gumbov: vsebino pripravi FavoritesWidgetService (unikaten URI na primerek widgeta)
                val adapter = Intent(context, FavoritesWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.fav_grid, adapter)
                views.setEmptyView(R.id.fav_grid, R.id.fav_empty)
                // Predloga za tap na gumb; uid doda vsak gumb (fill-in intent), zato mora biti MUTABLE
                views.setPendingIntentTemplate(
                    R.id.fav_grid,
                    PendingIntent.getBroadcast(
                        context, id,
                        Intent(context, FavoritesWidgetProvider::class.java).setAction(ACTION_ADD),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )
                val openApp = PendingIntent.getActivity(
                    context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.fav_header, openApp)
                views.setOnClickPendingIntent(R.id.fav_empty, openApp)
                views.setOnClickPendingIntent(
                    R.id.fav_add,
                    PendingIntent.getActivity(
                        context, 11, MainActivity.intent(context, "expense"),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                manager.updateAppWidget(id, views)
            }
        }

        private fun toast(context: Context, text: String) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() }
        }
    }
}

/** Po izteku časa za razveljavitev osveži widget, da gumb "Razveljavi" izgine. */
class WidgetUndoExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        FavoritesWidgetProvider.refresh(applicationContext)
        return Result.success()
    }
}
