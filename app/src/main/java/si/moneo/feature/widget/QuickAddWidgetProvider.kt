package si.moneo.feature.widget

import si.moneo.ui.str
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import si.moneo.MainActivity
import si.moneo.MoneoApp
import si.moneo.R
import si.moneo.data.db.entity.TransactionType
import si.moneo.feature.voice.VoiceInputActivity
import si.moneo.ui.formatCents
import java.time.YearMonth

/**
 * Widget na domačem zaslonu: taba Odhodki | Prihodki (kot kartica stanja v aplikaciji, privzeto odhodki)
 * z vsoto tekočega meseca brez predznaka + glasovni vnos, hiter strošek, hiter prihodek (v barvah teme).
 */
class QuickAddWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAB) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val income = intent.getBooleanExtra(EXTRA_INCOME, false)
        val pending = goAsync()
        scope.launch {
            try {
                (context.applicationContext as MoneoApp).container.prefs.setWidgetShowsIncome(id, income)
                update(context, AppWidgetManager.getInstance(context), intArrayOf(id))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = (context.applicationContext as MoneoApp).container.prefs
        appWidgetIds.forEach { prefs.setWidgetShowsIncome(it, false) }
    }

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

    companion object {
        private const val ACTION_TAB = "si.moneo.widget.QUICK_ADD_TAB"
        private const val EXTRA_INCOME = "income"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Osveži vse primerke widgeta (kliče se ob spremembi transakcij). */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickAddWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as MoneoApp
            val txs = app.container.repository.transactionsForMonth(YearMonth.now()).filter { it.confirmed }
            val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
            val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }

            val prefs = app.container.prefs
            val hidden = prefs.hideBalance
            val c = widgetColors(context)
            for (id in ids) {
                val showIncome = prefs.widgetShowsIncome(id)
                val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
                // Vsota izbrane vrste brez predznaka (kot na kartici stanja v aplikaciji)
                views.setTextViewText(R.id.widget_balance, if (hidden) "•••• €" else formatCents(if (showIncome) income else expense))
                views.setTextViewText(
                    R.id.widget_label,
                    str(if (hidden) R.string.balance_hidden else if (showIncome) R.string.widget_month_income else R.string.widget_month),
                )
                // Besedila iz kode: zaganjalnik bi postavitev sicer razrešil v jeziku sistema
                views.setTextViewText(R.id.tab_expense_text, str(R.string.widget_tab_expense))
                views.setTextViewText(R.id.tab_income_text, str(R.string.widget_tab_income))
                views.setTextViewText(R.id.btn_expense_text, str(R.string.widget_btn_expense))
                views.setTextViewText(R.id.btn_income_text, str(R.string.widget_btn_income))
                views.setContentDescription(R.id.btn_voice, str(R.string.tile_label))

                // Taba: izbrani je obarvan (rdeče/zeleno), drugi le besedilo
                val selected = if (showIncome) c.income else c.expense
                views.tint(R.id.tabs_bg, c.track)
                views.setViewVisibility(R.id.tab_expense_bg, if (showIncome) View.INVISIBLE else View.VISIBLE)
                views.setViewVisibility(R.id.tab_income_bg, if (showIncome) View.VISIBLE else View.INVISIBLE)
                views.tint(R.id.tab_expense_bg, selected)
                views.tint(R.id.tab_income_bg, selected)
                views.setTextColor(R.id.tab_expense_text, if (showIncome) c.subtext else Color.WHITE)
                views.setTextColor(R.id.tab_income_text, if (showIncome) Color.WHITE else c.subtext)
                views.setOnClickPendingIntent(R.id.tab_expense, tabIntent(context, id, income = false))
                views.setOnClickPendingIntent(R.id.tab_income, tabIntent(context, id, income = true))

                // Barve izbrane teme aplikacije
                views.tint(R.id.widget_bg, c.background)
                views.setTextColor(R.id.widget_label, c.subtext)
                views.setTextColor(R.id.widget_balance, c.text)
                views.tint(R.id.btn_voice_bg, c.accent)
                views.tint(R.id.btn_voice_icon, c.onAccent)
                views.tint(R.id.btn_expense_bg, c.expenseBg)
                views.setTextColor(R.id.btn_expense_text, c.expense)
                views.tint(R.id.btn_income_bg, c.incomeBg)
                views.setTextColor(R.id.btn_income_text, c.income)

                views.setOnClickPendingIntent(R.id.widget_summary, pendingActivity(
                    context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ))
                views.setOnClickPendingIntent(R.id.btn_voice, pendingActivity(
                    context, 0, Intent(context, VoiceInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ))
                views.setOnClickPendingIntent(R.id.btn_expense, pendingActivity(context, 1, MainActivity.intent(context, "expense")))
                views.setOnClickPendingIntent(R.id.btn_income, pendingActivity(context, 2, MainActivity.intent(context, "income")))

                manager.updateAppWidget(id, views)
            }
        }

        /** Tap na tab: zapomni izbiro za ta widget in ga osveži. */
        private fun tabIntent(context: Context, widgetId: Int, income: Boolean): PendingIntent =
            PendingIntent.getBroadcast(
                context, widgetId * 2 + if (income) 1 else 0,
                Intent(context, QuickAddWidgetProvider::class.java)
                    .setAction(ACTION_TAB)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(EXTRA_INCOME, income),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun pendingActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
