package si.moneo.ui.more

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import si.moneo.ui.AppLocale
import androidx.compose.material.icons.rounded.Language
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import si.moneo.ui.home.HomeLayoutSheet
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.work.ExistingWorkPolicy
import si.moneo.feature.reminder.DailyReminderWorker
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.moneo.MoneoApp
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.feature.ocr.ReceiptScanActivity
import si.moneo.feature.voice.VoiceInputActivity
import si.moneo.ui.MainViewModel
import si.moneo.ui.SubscriptionSuggestion
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.DateField
import si.moneo.ui.components.LocalSnackbar
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.SettingsRow
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.centsToInput
import si.moneo.ui.formatCents
import si.moneo.ui.formatDate
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.parseCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.ThemeAccent
import si.moneo.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MoreScreen(
    vm: MainViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    onAccentChange: (ThemeAccent) -> Unit,
    appLock: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenFavorites: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MoneoApp
    val prefs = app.container.prefs
    val backup = app.container.backup
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val hideBalance by vm.hideBalance.collectAsStateWithLifecycle()
    var showQuickHelp by remember { mutableStateOf(false) }
    var listenerEnabled by remember { mutableStateOf(false) }
    var backupFolder by remember { mutableStateOf(prefs.backupFolder) }
    var lastBackup by remember { mutableLongStateOf(prefs.lastBackupAt) }
    var monthlySummary by remember { mutableStateOf(prefs.monthlySummary) }
    var dailyReminder by remember { mutableStateOf(prefs.dailyReminder) }
    var reminderMinutes by remember { mutableIntStateOf(prefs.dailyReminderMinutes) }
    var pickReminderTime by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    val selectedLanguage = remember { AppLocale.selected(context) }
    val homeLayout by vm.homeLayout.collectAsStateWithLifecycle()
    var editHomeLayout by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LifecycleResumeEffect(Unit) {
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        onPauseOrDispose {}
    }
    val colors = Finance.colors
    val primary = MaterialTheme.colorScheme.primary

    /** Izvede opravilo v ozadju in pokaže rezultat ali napako v snackbarju. */
    fun runTask(block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                str(R.string.error_prefix, e.message ?: e::class.simpleName.orEmpty())
            }
            busy = false
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { runTask { str(R.string.csv_exported, backup.writeCsv(it)) } }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            runTask {
                backup.writeJson(it)
                str(R.string.backup_saved)
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runTask {
                val r = backup.restoreJson(it)
                str(R.string.restore_result, r.inserted, r.updated, r.skipped)
            }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.backupFolder = uri
        backupFolder = uri
        // Takoj naredi prvo kopijo, da uporabnik vidi, da deluje
        runTask {
            val name = backup.writeToFolder(uri)
            prefs.lastBackupAt = System.currentTimeMillis()
            lastBackup = prefs.lastBackupAt
            str(R.string.auto_backup_enabled, name)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(Modifier.statusBarsPadding().padding(top = Spacing.md)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
            },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Group(stringResource(R.string.group_appearance), query, stringResource(R.string.group_appearance_kw)) {
            // Jezik: privzeto kot telefon (angleščina, če jezik telefona ni podprt)
            SettingsRow(
                Icons.Rounded.Language, primary, stringResource(R.string.language),
                selectedLanguage?.let { AppLocale.displayName(it) }
                    ?: stringResource(R.string.language_system_current, AppLocale.displayName(LocalConfiguration.current.locales[0].toLanguageTag())),
                onClick = { pickLanguage = true },
            )
            Divider()
            SettingsRow(Icons.Rounded.Palette, colors.warning, stringResource(R.string.theme))
            SegmentedTabs(
                ThemeMode.entries.toList(), themeMode, onThemeChange,
                label = { str(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }) },
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
            )
            Divider()
            SettingsRow(
                Icons.Rounded.Dashboard, colors.income, stringResource(R.string.home_screen),
                stringResource(R.string.home_cards_visible, homeLayout.count { it.visible }, homeLayout.size),
                onClick = { editHomeLayout = true },
            )
            Divider()
            SettingsRow(Icons.Rounded.ColorLens, MaterialTheme.colorScheme.primary, stringResource(R.string.colour_theme), themeAccent.label)
            AccentPicker(themeAccent, onAccentChange, Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md))
        }

        Group(stringResource(R.string.group_basic_data), query, stringResource(R.string.group_basic_data_kw)) {
            SettingsRow(Icons.Rounded.Category, colors.income, stringResource(R.string.categories_and_budgets), stringResource(R.string.categories_and_budgets_sub), onClick = onOpenCategories)
            Divider()
            SettingsRow(Icons.Rounded.AccountBalanceWallet, colors.warning, stringResource(R.string.accounts), stringResource(R.string.accounts_sub), onClick = onOpenAccounts)
            Divider()
            SettingsRow(
                Icons.Rounded.Bolt, colors.warning, stringResource(R.string.favorites),
                if (favorites.isEmpty()) stringResource(R.string.favorites_empty_sub) else stringResource(R.string.favorites_count_sub, favorites.size),
                onClick = onOpenFavorites,
            )
        }

        Group(stringResource(R.string.group_notifications), query, stringResource(R.string.group_notifications_kw)) {
            SettingsRow(
                Icons.Rounded.Alarm, colors.warning, stringResource(R.string.evening_reminder),
                if (dailyReminder) stringResource(R.string.evening_reminder_on, formatMinutes(reminderMinutes))
                else stringResource(R.string.evening_reminder_off),
                trailing = {
                    Switch(checked = dailyReminder, onCheckedChange = {
                        dailyReminder = it
                        prefs.dailyReminder = it
                        DailyReminderWorker.schedule(context, prefs, ExistingWorkPolicy.REPLACE)
                    })
                },
                onClick = { pickReminderTime = true },
            )
            Divider()
            SettingsRow(
                Icons.Rounded.Summarize, colors.income, stringResource(R.string.monthly_summary),
                stringResource(R.string.monthly_summary_sub),
                trailing = {
                    Switch(checked = monthlySummary, onCheckedChange = {
                        monthlySummary = it
                        prefs.monthlySummary = it
                    })
                },
            )
            Divider()
            SettingsRow(
                Icons.Rounded.NotificationsActive,
                if (listenerEnabled) colors.income else colors.warning,
                stringResource(if (listenerEnabled) R.string.bank_reading_on else R.string.bank_reading_off),
                stringResource(R.string.bank_reading_sub),
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
        }

        Group(stringResource(R.string.group_security), query, stringResource(R.string.group_security_kw)) {
            SettingsRow(
                Icons.Rounded.Fingerprint, primary, stringResource(R.string.app_lock),
                stringResource(if (appLockAvailable) R.string.app_lock_sub else R.string.app_lock_unavailable),
                trailing = { Switch(checked = appLock, enabled = appLockAvailable, onCheckedChange = onAppLockChange) },
            )
            Divider()
            SettingsRow(
                Icons.Rounded.VisibilityOff, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.hide_balance_title),
                stringResource(R.string.hide_balance_sub),
                trailing = { Switch(checked = hideBalance, onCheckedChange = vm::setHideBalance) },
            )
        }

        Group(stringResource(R.string.group_backup), query, stringResource(R.string.group_backup_kw)) {
            SettingsRow(Icons.Rounded.TableChart, colors.income, stringResource(R.string.export_csv), stringResource(R.string.export_csv_sub)) {
                if (!busy) csvLauncher.launch("moneo-${LocalDate.now()}.csv")
            }
            Divider()
            SettingsRow(Icons.Rounded.Backup, primary, stringResource(R.string.create_backup), stringResource(R.string.create_backup_sub)) {
                if (!busy) jsonLauncher.launch("moneo-${LocalDate.now()}.json")
            }
            Divider()
            SettingsRow(Icons.Rounded.Restore, colors.warning, stringResource(R.string.restore_backup), stringResource(R.string.restore_backup_sub)) {
                if (!busy) restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
            Divider()
            SettingsRow(
                Icons.Rounded.FolderOpen,
                if (backupFolder != null) colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                stringResource(R.string.auto_backup),
                when {
                    backupFolder == null -> stringResource(R.string.auto_backup_off)
                    lastBackup > 0 -> stringResource(R.string.auto_backup_last, formatDate(lastBackup))
                    else -> stringResource(R.string.enabled)
                },
                trailing = {
                    Switch(checked = backupFolder != null, onCheckedChange = { on ->
                        if (on) folderLauncher.launch(null)
                        else {
                            prefs.backupFolder = null
                            backupFolder = null
                        }
                    })
                },
            )
        }

        Group(stringResource(R.string.group_help), query, stringResource(R.string.group_help_kw)) {
            SettingsRow(Icons.Rounded.Widgets, colors.expense, stringResource(R.string.help_widgets), if (showQuickHelp) null else stringResource(R.string.help_widgets_sub)) {
                showQuickHelp = !showQuickHelp
            }
            if (showQuickHelp) {
                Text(
                    stringResource(R.string.help_widgets_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 68.dp, end = Spacing.lg, bottom = Spacing.md),
                )
            }
        }

        Group(stringResource(R.string.group_about), query, stringResource(R.string.group_about_kw)) {
            SettingsRow(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.app_name), stringResource(R.string.about_sub))
        }
    }

    if (editHomeLayout) {
        HomeLayoutSheet(homeLayout, onChange = vm::setHomeLayout, onDismiss = { editHomeLayout = false })
    }

    if (pickLanguage) {
        LanguageDialog(
            selected = selectedLanguage,
            onDismiss = { pickLanguage = false },
            onSelect = { tag ->
                pickLanguage = false
                (context as? android.app.Activity)?.let { AppLocale.set(it, tag) }
            },
        )
    }

    if (pickReminderTime) {
        ReminderTimeDialog(
            minutes = reminderMinutes,
            onDismiss = { pickReminderTime = false },
            onConfirm = { m ->
                reminderMinutes = m
                prefs.dailyReminderMinutes = m
                // Izbira ure hkrati vklopi opomnik
                dailyReminder = true
                prefs.dailyReminder = true
                DailyReminderWorker.schedule(context, prefs, ExistingWorkPolicy.REPLACE)
                pickReminderTime = false
            },
        )
    }


}

/** Izbira jezika: "Kot telefon" + vsi podprti jeziki (vsak v svojem jeziku). */
@Composable
private fun LanguageDialog(selected: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val options = listOf<String?>(null) + AppLocale.SUPPORTED
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_language)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(420.dp)) {
                items(options.size) { i ->
                    val tag = options[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).clickable { onSelect(tag) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = tag == selected, onClick = { onSelect(tag) })
                        Spacer(Modifier.width(8.dp))
                        Text(if (tag == null) stringResource(R.string.language_system) else AppLocale.displayName(tag), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatMinutes(m: Int) = "%d:%02d".format(m / 60, m % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_title)) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Barvni krogi za izbiro teme; izbrani ima kljukico. */
@Composable
private fun AccentPicker(selected: ThemeAccent, onSelect: (ThemeAccent) -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    // Material You: krog iz sistemskih barv (primarna, terciarna, sekundarna)
    val dynamicBrush = remember(context) {
        if (ThemeAccent.dynamicSupported) {
            val s = androidx.compose.material3.dynamicLightColorScheme(context)
            Brush.sweepGradient(listOf(s.primary, s.tertiary, s.secondary, s.primary))
        } else null
    }
    // Vodoravno drsenje, ker 8 krogov na ozkih zaslonih ne gre v eno vrsto
    androidx.compose.foundation.lazy.LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ThemeAccent.available.size) { i ->
            val accent = ThemeAccent.available[i]
            val spec = if (dark) accent.dark else accent.light
            val isSelected = accent == selected
            val brush = if (accent == ThemeAccent.DYNAMIC && dynamicBrush != null) dynamicBrush else Brush.linearGradient(listOf(spec.heroStart, spec.heroEnd))
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(brush)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable(onClickLabel = accent.label) { onSelect(accent) }
                    .semantics { contentDescription = accent.label; this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isSelected -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    accent == ThemeAccent.DYNAMIC -> Icon(Icons.Rounded.Wallpaper, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Zložljiva skupina nastavitev. Med iskanjem se prikažejo le skupine, katerih naslov ali
 * ključne besede vsebujejo iskani niz (in so takrat vedno razprte).
 */
@Composable
private fun Group(title: String, query: String, keywords: String, content: @Composable () -> Unit) {
    val q = query.trim()
    if (q.isNotEmpty() && !"$title $keywords".contains(q, ignoreCase = true)) return
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val open = expanded || q.isNotEmpty()
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "chevron")
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xs)).clickable { expanded = !expanded }
                .padding(start = Spacing.sm, bottom = Spacing.sm, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ExpandMore, stringResource(if (open) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(open) {
            SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
