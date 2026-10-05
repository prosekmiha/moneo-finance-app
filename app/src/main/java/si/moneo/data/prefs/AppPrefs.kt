package si.moneo.data.prefs

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** Preproste nastavitve aplikacije (SharedPreferences), dostopne tudi iz workerjev. */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    /** Mapa za samodejne varnostne kopije (SAF tree URI) ali null, če je izklopljeno. */
    var backupFolder: Uri?
        get() = sp.getString("backup_folder", null)?.let(Uri::parse)
        set(v) = sp.edit { putString("backup_folder", v?.toString()) }

    var lastBackupAt: Long
        get() = sp.getLong("last_backup_at", 0)
        set(v) = sp.edit { putLong("last_backup_at", v) }

    var appLock: Boolean
        get() = sp.getBoolean("app_lock", false)
        set(v) = sp.edit { putBoolean("app_lock", v) }

    /** Cilj, v katerega gre zaokrožitev stroškov (null = izklopljeno). */
    var roundUpGoalUid: String?
        get() = sp.getString("round_up_goal", null)
        set(v) = sp.edit { putString("round_up_goal", v) }

    /** Skrij zneske stanja na zaslonu (zasebnost, npr. v javnosti). */
    var hideBalance: Boolean
        get() = sp.getBoolean("hide_balance", false)
        set(v) = sp.edit { putBoolean("hide_balance", v) }

    var monthlySummary: Boolean
        get() = sp.getBoolean("monthly_summary", true)
        set(v) = sp.edit { putBoolean("monthly_summary", v) }

    /** Namig "dolg pritisk na + odpre hiter meni" je bil že prikazan. */
    var quickMenuHintShown: Boolean
        get() = sp.getBoolean("quick_menu_hint", false)
        set(v) = sp.edit { putBoolean("quick_menu_hint", v) }

    /** Uvodni vodnik je bil zaključen ali preskočen. */
    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit { putBoolean("onboarding_done", v) }

    /** Ali smo že vprašali za dovoljenje za obvestila (vprašamo le enkrat). */
    var askedNotificationPermission: Boolean
        get() = sp.getBoolean("asked_notif_perm", false)
        set(v) = sp.edit { putBoolean("asked_notif_perm", v) }

    /** Skupni mesečni proračun za vse stroške v centih (null = ni nastavljen). */
    var monthlyBudgetCents: Long?
        get() = sp.getLong("monthly_budget", 0).takeIf { it > 0 }
        set(v) = sp.edit { putLong("monthly_budget", v ?: 0) }

    /** Postavitev kartic na Domov ("FAVORITES,-UPCOMING,..."; minus = skrito), null = privzeto. */
    var homeLayout: String?
        get() = sp.getString("home_layout", null)
        set(v) = sp.edit { putString("home_layout", v) }

    /** Večerni opomnik, če tisti dan ni bilo vnosa. */
    var dailyReminder: Boolean
        get() = sp.getBoolean("daily_reminder", false)
        set(v) = sp.edit { putBoolean("daily_reminder", v) }

    /** Ura opomnika v minutah od polnoči (privzeto 20:00). */
    var dailyReminderMinutes: Int
        get() = sp.getInt("daily_reminder_minutes", 20 * 60)
        set(v) = sp.edit { putInt("daily_reminder_minutes", v) }

    /** Ali widget za hiter vnos kaže prihodke (privzeto odhodki); izbira na primerek widgeta. */
    fun widgetShowsIncome(widgetId: Int): Boolean = sp.getBoolean("widget_income_$widgetId", false)

    fun setWidgetShowsIncome(widgetId: Int, income: Boolean) = sp.edit {
        if (income) putBoolean("widget_income_$widgetId", true) else remove("widget_income_$widgetId")
    }

    /**
     * Že poslani opomniki ("epochDay|ključ"), da se isti opomnik ne pošlje dvakrat, a se tudi ne
     * izgubi, ko dnevni worker kak dan ne teče. Starejši od 120 dni se pobrišejo.
     */
    fun wasReminded(key: String): Boolean =
        sp.getStringSet("reminded", emptySet()).orEmpty().any { it.substringAfter('|') == key }

    fun markReminded(key: String, today: java.time.LocalDate = java.time.LocalDate.now()) {
        val cutoff = today.toEpochDay() - 120
        val kept = sp.getStringSet("reminded", emptySet()).orEmpty()
            .filter { (it.substringBefore('|').toLongOrNull() ?: 0) >= cutoff }
        sp.edit { putStringSet("reminded", (kept + "${today.toEpochDay()}|$key").toSet()) }
    }

    /** Zadnji vnos iz widgeta s priljubljenimi (za "Razveljavi" v widgetu). */
    var widgetLastAddUid: String?
        get() = sp.getString("widget_last_add_uid", null)
        set(v) = sp.edit { putString("widget_last_add_uid", v) }

    var widgetLastAddLabel: String
        get() = sp.getString("widget_last_add_label", "").orEmpty()
        set(v) = sp.edit { putString("widget_last_add_label", v) }

    var widgetLastAddAt: Long
        get() = sp.getLong("widget_last_add_at", 0)
        set(v) = sp.edit { putLong("widget_last_add_at", v) }

    /** Zadnji mesec (yyyy-MM), za katerega je bil poslan povzetek. */
    var lastSummaryMonth: String?
        get() = sp.getString("last_summary_month", null)
        set(v) = sp.edit { putString("last_summary_month", v) }
}
