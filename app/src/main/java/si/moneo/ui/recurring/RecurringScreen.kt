package si.moneo.ui.recurring

import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.qty
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.MainViewModel
import si.moneo.ui.SubscriptionSuggestion
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.DateField
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.LocalSnackbar
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.centsToInput
import si.moneo.ui.components.shortDate
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.parseCents
import si.moneo.ui.relativeDayLabel
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

private class RuleEditTarget(val rule: RecurringRuleEntity?)

/**
 * Ponavljajoča plačila (najemnina, plača, zavarovanje ...): seznam pravil, zaznani redni prihodki
 * in urejevalnik. Naročnine (Netflix ...) imajo svoj zaslon.
 */
@Composable
fun RecurringScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rules by vm.activeRules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val allSuggestions by vm.subscriptionSuggestions.collectAsStateWithLifecycle()
    // Zaznani stroški gredo med naročnine, tu so redni prihodki (plača, regres ...)
    val suggestions = allSuggestions.filter { it.type == TransactionType.INCOME }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var ruleEditor by remember { mutableStateOf<RuleEditTarget?>(null) }
    val colors = Finance.colors

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.recurring_payments), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    stringResource(R.string.recurring_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val active = rules.filter { it.enabled }
            if (active.isNotEmpty()) {
                item {
                    val monthlyOut = active.filter { it.type == TransactionType.EXPENSE }.sumOf { monthlyEquivalent(it) }
                    val monthlyIn = active.filter { it.type == TransactionType.INCOME }.sumOf { monthlyEquivalent(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SoftCard(Modifier.weight(1f)) {
                            Text(stringResource(R.string.expenses_per_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatCents(monthlyOut), style = MaterialTheme.typography.titleLarge, color = colors.expense)
                        }
                        SoftCard(Modifier.weight(1f)) {
                            Text(stringResource(R.string.income_per_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatCents(monthlyIn), style = MaterialTheme.typography.titleLarge, color = colors.income)
                        }
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.detected_regular_income), Modifier.padding(top = Spacing.sm)) }
                items(suggestions.take(5), key = { "s-" + it.key }) { s ->
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        SuggestionRow(s, onAccept = {
                            vm.acceptSuggestion(s)
                            scope.launch { snackbar.showSnackbar(str(R.string.added_named, s.title)) }
                        }, onDismiss = { vm.dismissSuggestion(s) })
                    }
                }
            }
            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Autorenew, stringResource(R.string.no_recurring),
                        stringResource(R.string.no_recurring_hint),
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.rules_count, rules.size), Modifier.padding(top = Spacing.sm)) }
            }
            items(rules, key = { it.uid }) { rule ->
                val cat = categories.firstOrNull { it.uid == rule.categoryUid }
                val next = millisToLocalDate(rule.nextDueDate)
                SoftCard(Modifier.fillMaxWidth(), onClick = { ruleEditor = RuleEditTarget(rule) }, contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(cat?.title ?: rule.title, cat?.color, type = rule.type, size = 40.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(rule.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${frequencyLabel(rule.frequency).replaceFirstChar { it.uppercase() }} · " +
                                    if (rule.enabled) stringResource(R.string.next_on, shortDate(next), relativeDayLabel(next)) else stringResource(R.string.disabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            (if (rule.type == TransactionType.INCOME) "+" else "−") + formatCents(rule.amountCents),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                            color = if (rule.type == TransactionType.INCOME) colors.income else MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(checked = rule.enabled, onCheckedChange = { vm.toggleRule(rule, it) }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                PillButton(
                    stringResource(R.string.new_recurring_payment), Icons.Rounded.Add, { ruleEditor = RuleEditTarget(null) },
                    Modifier.fillMaxWidth().padding(top = Spacing.sm), filled = true,
                )
            }
        }
    }

    ruleEditor?.let { target ->
        RuleSheet(
            rule = target.rule,
            categories = categories,
            accounts = accounts,
            onDismiss = { ruleEditor = null },
            onSave = {
                vm.saveRule(it)
                ruleEditor = null
            },
            onDelete = target.rule?.let { r ->
                {
                    vm.deleteRule(r)
                    ruleEditor = null
                }
            },
        )
    }
}

/** Znesek pravila, preračunan na mesec (za povzetek). */
private fun monthlyEquivalent(rule: RecurringRuleEntity): Long {
    val n = rule.interval.coerceAtLeast(1)
    return when (rule.frequency) {
        RecurrenceFrequency.DAILY -> rule.amountCents * 365 / 12 / n
        RecurrenceFrequency.WEEKLY -> rule.amountCents * 52 / 12 / n
        RecurrenceFrequency.MONTHLY -> rule.amountCents / n
        RecurrenceFrequency.YEARLY -> rule.amountCents / (12L * n)
    }
}

fun frequencyLabel(f: RecurrenceFrequency) = when (f) {
    RecurrenceFrequency.DAILY -> str(R.string.freq_daily)
    RecurrenceFrequency.WEEKLY -> str(R.string.freq_weekly)
    RecurrenceFrequency.MONTHLY -> str(R.string.freq_monthly)
    RecurrenceFrequency.YEARLY -> str(R.string.freq_yearly)
}

@Composable
private fun SuggestionRow(s: SubscriptionSuggestion, onAccept: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = 4.dp, top = Spacing.md, bottom = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box {
            CategoryIcon(s.title, type = s.type, size = 40.dp)
            Icon(
                Icons.Rounded.AutoAwesome, null, tint = Finance.colors.warning,
                modifier = Modifier.align(Alignment.TopEnd).padding(0.dp).width(14.dp).height(14.dp),
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
        TextButton(onClick = onAccept) { Text(stringResource(R.string.add)) }
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.hide), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    rule: RecurringRuleEntity?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (RecurringRuleEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(rule?.title ?: "") }
    var amount by remember { mutableStateOf(rule?.amountCents?.let(::centsToInput) ?: "") }
    var type by remember { mutableStateOf(rule?.type ?: TransactionType.EXPENSE) }
    var frequency by remember { mutableStateOf(rule?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var autoAdd by remember { mutableStateOf(rule?.autoAdd ?: true) }
    var categoryUid by remember { mutableStateOf(rule?.categoryUid) }
    var accountUid by remember { mutableStateOf(rule?.accountUid) }
    var confirmDelete by remember { mutableStateOf(false) }
    val originalNext = rule?.nextDueDate?.let(::millisToLocalDate)
    var nextDate by remember { mutableStateOf(originalNext ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(rule?.endDate?.let(::millisToLocalDate)) }
    val cents = parseCents(amount) ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(if (rule == null) R.string.new_recurring_rule else R.string.edit_rule), style = MaterialTheme.typography.titleLarge)
            SegmentedTabs(
                listOf(TransactionType.EXPENSE, TransactionType.INCOME), type,
                {
                    type = it
                    if (categories.firstOrNull { c -> c.uid == categoryUid }?.type != it) categoryUid = null
                },
                label = { str(if (it == TransactionType.EXPENSE) R.string.entry_expense else R.string.entry_income) },
            )
            OutlinedTextField(
                title, { title = it }, label = { Text(stringResource(R.string.name_hint_rent)) },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                amount, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) amount = v },
                label = { Text(stringResource(R.string.amount_eur)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DropdownField(
                    stringResource(R.string.category),
                    listOf<Pair<String?, String>>(null to stringResource(R.string.none)) + categories.filter { it.type == type }.map { it.uid to it.title },
                    categoryUid, { categoryUid = it }, Modifier.weight(1f),
                )
                DropdownField(
                    stringResource(R.string.account),
                    listOf<Pair<String?, String>>(null to stringResource(R.string.default_short)) + accounts.map { it.uid to it.title },
                    accountUid, { accountUid = it }, Modifier.weight(1f),
                )
            }
            SegmentedTabs(RecurrenceFrequency.entries.toList(), frequency, { frequency = it }, label = { frequencyLabel(it).replaceFirstChar(Char::uppercase) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.record_auto_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (autoAdd) R.string.rule_auto_on else R.string.rule_auto_off),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoAdd, onCheckedChange = { autoAdd = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DateField(stringResource(R.string.next_due), nextDate, { if (it != null) nextDate = it }, Modifier.weight(1f))
                DateField(stringResource(R.string.end_optional), endDate, { endDate = it }, Modifier.weight(1f), clearable = true)
            }
            if (nextDate.isBefore(LocalDate.now())) {
                Text(
                    stringResource(R.string.rule_past_date, shortDate(nextDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Finance.colors.warning,
                )
            }
            PillButton(
                stringResource(R.string.save), Icons.Rounded.Check,
                onClick = {
                    onSave(
                        (rule ?: RecurringRuleEntity(
                            title = "", type = type, amountCents = 0, frequency = frequency,
                            nextDueDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        )).copy(
                            title = title.trim().ifBlank { str(R.string.rule_default) },
                            type = type,
                            amountCents = cents,
                            frequency = frequency,
                            nextDueDate = nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            // Nespremenjen datum ohrani dan obračuna (npr. 31., ko je termin 28. 2.)
                            billingDay = if (rule != null && nextDate == originalNext) (rule.billingDay ?: nextDate.dayOfMonth) else nextDate.dayOfMonth,
                            endDate = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                            autoAdd = autoAdd,
                            categoryUid = categoryUid,
                            accountUid = accountUid,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                filled = true,
                enabled = cents > 0,
            )
            if (onDelete != null) {
                TextButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(if (confirmDelete) R.string.tap_again_to_confirm else R.string.delete_rule), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
