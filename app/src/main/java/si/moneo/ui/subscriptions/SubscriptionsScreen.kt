package si.moneo.ui.subscriptions

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.qty
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.defaultAccount
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.repo.FinanceRepository
import si.moneo.domain.subscriptions.BillingCycle
import si.moneo.domain.subscriptions.billingLabel
import si.moneo.domain.subscriptions.hasEnded
import si.moneo.domain.subscriptions.monthlyCents
import si.moneo.domain.subscriptions.paymentsUntil
import si.moneo.ui.MainViewModel
import si.moneo.ui.SubscriptionSuggestion
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.DateField
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.shortDate
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.centsToInput
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.parseCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Kaj ureja urejevalnik: obstoječo naročnino, predlog ali novo (oboje null). */
private class EditTarget(val subscription: SubscriptionEntity?, val suggestion: SubscriptionSuggestion? = null)

@Composable
fun SubscriptionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val subs by vm.subscriptions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val allSuggestions by vm.subscriptionSuggestions.collectAsStateWithLifecycle()
    val suggestions = allSuggestions.filter { it.type == TransactionType.EXPENSE }
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    val running = subs.filter { it.active && !it.hasEnded }
    val stopped = subs.filter { !it.active || it.hasEnded }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.subscriptions), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (running.isNotEmpty()) item(key = "summary") { SummaryCard(running) }

            if (suggestions.isNotEmpty()) {
                item(key = "suggestions-header") { SectionHeader(stringResource(R.string.detected_subscriptions), Modifier.padding(top = Spacing.sm)) }
                items(suggestions.take(5), key = { "s-" + it.key }) { s ->
                    SuggestionCard(s, onAdd = { editing = EditTarget(null, s) }, onDismiss = { vm.dismissSuggestion(s) })
                }
            }

            if (subs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.Subscriptions,
                        stringResource(R.string.no_subscriptions),
                        stringResource(R.string.no_subscriptions_hint),
                    )
                }
            }
            if (running.isNotEmpty()) {
                item(key = "running-header") { SectionHeader(stringResource(R.string.active_count, running.size), Modifier.padding(top = Spacing.sm)) }
                items(running, key = { it.uid }) { sub ->
                    SubscriptionRow(sub, categories.firstOrNull { it.uid == sub.categoryUid }) { editing = EditTarget(sub) }
                }
            }
            if (stopped.isNotEmpty()) {
                item(key = "stopped-header") { SectionHeader(stringResource(R.string.stopped_and_ended), Modifier.padding(top = Spacing.sm)) }
                items(stopped, key = { it.uid }) { sub ->
                    SubscriptionRow(sub, categories.firstOrNull { it.uid == sub.categoryUid }) { editing = EditTarget(sub) }
                }
            }
            item(key = "add") {
                PillButton(
                    stringResource(R.string.new_subscription), Icons.Rounded.Add, { editing = EditTarget(null) },
                    Modifier.fillMaxWidth().padding(top = Spacing.sm), filled = true,
                )
            }
        }
    }

    editing?.let { target ->
        val sub = target.subscription
        SubscriptionEditorSheet(
            subscription = sub,
            suggestion = target.suggestion,
            categories = categories.filter { it.type == TransactionType.EXPENSE },
            accounts = accounts,
            onDismiss = { editing = null },
            onSave = {
                vm.saveSubscription(it)
                editing = null
            },
            onToggleActive = sub?.let { s ->
                {
                    vm.setSubscriptionActive(s, !s.active)
                    editing = null
                }
            },
            onDelete = sub?.let { s ->
                {
                    vm.deleteSubscription(s)
                    editing = null
                }
            },
        )
    }
}

/** Povzetek: koliko naročnine stanejo na mesec/leto in katero plačilo je naslednje. */
@Composable
private fun SummaryCard(running: List<SubscriptionEntity>) {
    val monthly = running.sumOf { it.monthlyCents }
    val next = running.minByOrNull { it.nextPaymentDate }
    SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.xl), contentPadding = PaddingValues(20.dp)) {
        Text(stringResource(R.string.subscriptions_per_month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatCents(monthly), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Spacing.sm))
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.per_year), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCents(monthly * 12), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (next != null) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.next_payment), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${next.title} · ${relativeDay(millisToLocalDate(next.nextPaymentDate))}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(sub: SubscriptionEntity, category: CategoryEntity?, onClick: () -> Unit) {
    val muted = !sub.active || sub.hasEnded
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(sub.title, category?.color, type = TransactionType.EXPENSE, size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(sub.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        sub.hasEnded -> stringResource(R.string.ended_on, formatDay(millisToLocalDate(sub.endDate!!)))
                        !sub.active -> stringResource(R.string.paused_dot, sub.billingLabel)
                        else -> "${sub.billingLabel.replaceFirstChar { it.uppercase() }} · ${relativeDay(millisToLocalDate(sub.nextPaymentDate))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCents(sub.amountCents),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (BillingCycle.of(sub.frequency, sub.interval) != BillingCycle.MONTHLY) {
                    Text(
                        stringResource(R.string.per_month_short, formatCents(sub.monthlyCents)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: SubscriptionSuggestion, onAdd: () -> Unit, onDismiss: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                CategoryIcon(s.title, type = s.type, size = 40.dp)
                Icon(
                    Icons.Rounded.AutoAwesome, null, tint = Finance.colors.warning,
                    modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.suggestion_monthly, formatCents(s.amountCents), pluralStringResource(R.plurals.months_count, s.months, s.months)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.add)) }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.hide), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private val REMINDER_OPTIONS: List<Pair<Int?, String>>
    get() = listOf<Pair<Int?, String>>(null to str(R.string.reminder_none), 0 to str(R.string.reminder_same_day)) +
        listOf(1, 3, 7).map { it to str(R.string.reminder_days_before, qty(R.plurals.days_count, it, it)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionEditorSheet(
    subscription: SubscriptionEntity?,
    suggestion: SubscriptionSuggestion?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (SubscriptionEntity) -> Unit,
    onToggleActive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val originalNext = subscription?.nextPaymentDate?.let(::millisToLocalDate)
    var title by remember { mutableStateOf(subscription?.title ?: suggestion?.title ?: "") }
    var amount by remember { mutableStateOf((subscription?.amountCents ?: suggestion?.amountCents)?.let(::centsToInput) ?: "") }
    var cycle by remember {
        mutableStateOf(subscription?.let { BillingCycle.of(it.frequency, it.interval) } ?: BillingCycle.MONTHLY)
    }
    var nextDate by remember {
        mutableStateOf(
            originalNext ?: suggestion?.lastDate?.plusMonths(1)?.let { if (it.isBefore(today)) today else it } ?: today,
        )
    }
    var startDate by remember { mutableStateOf(subscription?.startDate?.let(::millisToLocalDate)) }
    var endDate by remember { mutableStateOf(subscription?.endDate?.let(::millisToLocalDate)) }
    // Privzeto kategorija "Naročnine" (če obstaja; sicer se ustvari ob shranjevanju)
    var categoryUid by remember {
        mutableStateOf(
            subscription?.categoryUid ?: suggestion?.categoryUid
                ?: categories.firstOrNull { it.title.equals(FinanceRepository.SUBSCRIPTION_CATEGORY, ignoreCase = true) }?.uid,
        )
    }
    var accountUid by remember { mutableStateOf(subscription?.accountUid ?: suggestion?.accountUid) }
    var remind by remember { mutableStateOf(if (subscription != null) subscription.remindDaysBefore else 1) }
    var url by remember { mutableStateOf(subscription?.url ?: "") }
    var note by remember { mutableStateOf(subscription?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = parseCents(amount) ?: 0

    fun build(): SubscriptionEntity {
        val zone = ZoneId.systemDefault()
        fun LocalDate.millis() = atStartOfDay(zone).toInstant().toEpochMilli()
        val base = subscription ?: SubscriptionEntity(title = "", amountCents = 0, nextPaymentDate = 0, billingDay = 1)
        return base.copy(
            title = title.trim(),
            amountCents = cents,
            frequency = cycle.frequency,
            interval = cycle.interval,
            nextPaymentDate = nextDate.millis(),
            // Nespremenjen datum ohrani dan obračuna (npr. 31., ko je naslednje plačilo 28. 2.)
            billingDay = if (subscription != null && nextDate == originalNext) subscription.billingDay else nextDate.dayOfMonth,
            startDate = startDate?.millis(),
            endDate = endDate?.millis(),
            categoryUid = categoryUid,
            accountUid = accountUid,
            remindDaysBefore = remind,
            url = url.trim(),
            note = note.trim(),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(if (subscription == null) R.string.new_subscription else R.string.edit_subscription), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                title, { title = it }, label = { Text(stringResource(R.string.name_hint_netflix)) },
                singleLine = true, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                amount, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) amount = v },
                label = { Text(stringResource(R.string.amount_per_payment)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DropdownField(
                    stringResource(R.string.billing),
                    BillingCycle.entries.map { it to it.label.replaceFirstChar(Char::uppercase) },
                    cycle, { cycle = it }, Modifier.weight(1f),
                )
                DateField(stringResource(R.string.next_payment), nextDate, { if (it != null) nextDate = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val hasSubscriptionCategory = categories.any { it.title.equals(FinanceRepository.SUBSCRIPTION_CATEGORY, ignoreCase = true) }
                DropdownField(
                    stringResource(R.string.category),
                    // null = kategorija "Naročnine" se ustvari ob shranjevanju
                    (if (hasSubscriptionCategory) emptyList() else listOf<Pair<String?, String>>(null to FinanceRepository.SUBSCRIPTION_CATEGORY)) +
                        categories.map { it.uid to it.title },
                    categoryUid, { categoryUid = it }, Modifier.weight(1f),
                )
                DropdownField(
                    stringResource(R.string.account),
                    listOf<Pair<String?, String>>(null to stringResource(R.string.default_short) + accounts.defaultAccount()?.let { " (${it.title})" }.orEmpty()) + accounts.map { it.uid to it.title },
                    accountUid, { accountUid = it }, Modifier.weight(1f),
                )
            }
            DropdownField(stringResource(R.string.reminder_before_payment), REMINDER_OPTIONS, remind, { remind = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DateField(stringResource(R.string.subscription_start), startDate, { startDate = it }, Modifier.weight(1f), clearable = true)
                DateField(stringResource(R.string.end_cancellation), endDate, { endDate = it }, Modifier.weight(1f), clearable = true)
            }
            OutlinedTextField(
                url, { url = it.trim() }, label = { Text(stringResource(R.string.manage_link)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    if (url.isNotBlank()) IconButton(onClick = {
                        val full = if (url.startsWith("http")) url else "https://$url"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full))) }
                    }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.open_link)) }
                },
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                note, { note = it }, label = { Text(stringResource(R.string.note_family_plan)) },
                shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
            )

            if (cents > 0) {
                val preview = build()
                val overdue = preview.paymentsUntil(today.minusDays(1)).size
                Text(
                    buildString {
                        append(str(R.string.sub_preview, formatDay(nextDate), formatCents(cents)))
                        if (overdue > 0) append(str(R.string.sub_preview_overdue, qty(R.plurals.missed_payments, overdue, overdue)))
                        append(str(R.string.sub_preview_year, formatCents(preview.monthlyCents * 12)))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue > 0) Finance.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PillButton(
                stringResource(R.string.save), Icons.Rounded.Check,
                onClick = { onSave(build()) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                filled = true,
                enabled = cents > 0 && title.isNotBlank(),
            )
            if (subscription != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (onToggleActive != null && !subscription.hasEnded) {
                        TextButton(onClick = onToggleActive) {
                            Text(stringResource(if (subscription.active) R.string.pause_subscription else R.string.resume_subscription))
                        }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                            Text(stringResource(if (confirmDelete) R.string.tap_again_to_confirm else R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    stringResource(R.string.sub_pause_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

private fun formatDay(d: LocalDate): String = shortDate(d)

/** "danes", "jutri", "čez 5 dni (12. 10.)", "zamuja" ... */
private fun relativeDay(d: LocalDate): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        days < 0 -> str(R.string.due_since, formatDay(d))
        days == 0L -> str(R.string.relative_today)
        days == 1L -> str(R.string.relative_tomorrow)
        days < 7 -> qty(R.plurals.in_days, days.toInt(), days.toInt())
        else -> formatDay(d)
    }
}
