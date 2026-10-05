package si.moneo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import si.moneo.data.db.seedDefaultsIfEmpty
import si.moneo.di.AppContainer
import si.moneo.ui.AppLocale
import si.moneo.ui.L10n
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import si.moneo.feature.backup.BackupWorker
import si.moneo.feature.debts.DebtReminderWorker
import si.moneo.feature.recurring.RecurringWorker
import si.moneo.feature.reminder.DailyReminderWorker
import si.moneo.feature.subscriptions.SubscriptionWorker
import si.moneo.feature.summary.MonthlySummaryWorker
import si.moneo.feature.widget.FavoritesWidgetProvider
import si.moneo.feature.widget.QuickAddWidgetProvider
import java.util.concurrent.TimeUnit

@OptIn(FlowPreview::class)
class MoneoApp : Application() {

    lateinit var container: AppContainer
        private set

    // Android < 13: jezik, izbran v aplikaciji (13+ to ureja sistem)
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        L10n.init(this)
        // Jezik se je morda spremenil: imena kanalov obvestil in besedila widgetov v novem jeziku
        createNotificationChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            QuickAddWidgetProvider.refresh(this@MoneoApp)
            FavoritesWidgetProvider.refresh(this@MoneoApp)
        }
    }

    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        container = AppContainer(this)
        createNotificationChannels()
        scheduleWorkers()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            seedDefaultsIfEmpty(container.database)
            // Zapadle naročnine zapiši takoj ob zagonu (dnevni worker lahko teče šele čez nekaj ur)
            container.repository.chargeDueSubscriptions()
            // Zapadla ponavljajoča pravila takoj ob zagonu (dnevni worker lahko teče šele čez nekaj ur)
            RecurringWorker.processAndNotify(this@MoneoApp)
            container.repository.applyAutoSavings()
        }
        // Widgeta prikazujeta stanje meseca/dneva - osveži ju ob vsaki spremembi transakcij
        scope.launch {
            container.repository.allTransactions.debounce(1000).collect {
                QuickAddWidgetProvider.refresh(this@MoneoApp)
                FavoritesWidgetProvider.refresh(this@MoneoApp)
            }
        }
        scope.launch {
            container.repository.allFavorites.debounce(500).collect { FavoritesWidgetProvider.refresh(this@MoneoApp) }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECURRING, getString(R.string.channel_recurring), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DETECTED, getString(R.string.channel_imported), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUBSCRIPTIONS, getString(R.string.channel_subscriptions), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEBTS, getString(R.string.channel_debts), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, getString(R.string.channel_summary), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun scheduleWorkers() {
        val wm = WorkManager.getInstance(this)
        DailyReminderWorker.schedule(this, container.prefs, androidx.work.ExistingWorkPolicy.KEEP)
        wm.enqueueUniquePeriodicWork(
            RecurringWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            SubscriptionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SubscriptionWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            DebtReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            MonthlySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MonthlySummaryWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
        )
    }

    companion object {
        const val CHANNEL_RECURRING = "recurring"
        const val CHANNEL_DETECTED = "detected"
        const val CHANNEL_SUMMARY = "summary"
        const val CHANNEL_SUBSCRIPTIONS = "subscriptions"
        const val CHANNEL_DEBTS = "debts"
        const val CHANNEL_REMINDERS = "reminders"
    }
}
