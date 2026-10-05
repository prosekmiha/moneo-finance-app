package si.moneo.ui.category

import si.moneo.ui.theme.asGraphic
import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.CategoryDetailState
import si.moneo.ui.CategoryYearComparison
import si.moneo.ui.percentDelta
import si.moneo.ui.OverallBudget
import si.moneo.ui.MainViewModel
import si.moneo.ui.TransactionUi
import si.moneo.ui.components.AnimatedAmount
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.IncomeExpenseBarChart
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ProgressRing
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.sharedElementKey
import si.moneo.ui.components.TransactionItem
import si.moneo.ui.components.centsToInput
import si.moneo.ui.formatCents
import si.moneo.ui.parseCents
import si.moneo.ui.theme.AccentPalette
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import kotlin.math.roundToInt

@Composable
fun CategoryDetailScreen(vm: MainViewModel, uid: String, onBack: () -> Unit, onEdit: (TransactionUi) -> Unit) {
    val flow = remember(uid) { vm.categoryDetail(uid) }
    val state by flow.collectAsState(CategoryDetailState())
    val cat = state.category
    var selected by remember(state.trend.size) { mutableIntStateOf((state.trend.size - 1).coerceAtLeast(0)) }
    var editBudget by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf(false) }
    val allCategories by vm.categories.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(cat?.title ?: "", onBack) {
            if (cat != null) IconButton(onClick = { editCategory = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit_category)) }
        }
        if (cat == null) return@Column
        val accent = accentFor(cat.title, cat.color)
        val isExpense = cat.type == TransactionType.EXPENSE
        val bucket = state.trend.getOrNull(selected)
        val visible = remember(state.transactions, bucket) { bucket?.let { b -> state.transactions.filter { it.date in b } } ?: state.transactions }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CategoryIcon(cat.title, cat.color, Modifier.sharedElementKey("cat-" + cat.uid), size = 72.dp, type = cat.type)
                    Spacer(Modifier.height(Spacing.md))
                    Text(stringResource(if (isExpense) R.string.spent_this_month else R.string.received_this_month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(state.monthSpentCents, style = MaterialTheme.typography.displayMedium)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    items(AccentPalette) { c ->
                        val isSel = accent == c
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c.asGraphic())
                                .border(3.dp, if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { vm.saveCategory(cat.copy(color = c.toArgb())) },
                        )
                    }
                }
            }
            if (isExpense) {
                item { BudgetCard(cat, state.monthSpentCents, onEdit = { editBudget = true }) }
            }
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                    Text(
                        bucket?.longLabel ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 4.dp, bottom = Spacing.sm),
                    )
                    IncomeExpenseBarChart(state.trend, selected, { selected = it }, height = 160.dp)
                }
            }
            state.yearComparison?.takeIf { it.ytdCents > 0 || it.hasLastYear }?.let { cmp ->
                item { YearComparisonCard(cmp, isExpense) }
            }
            item { SectionHeader(stringResource(R.string.transactions_count, visible.size)) }
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                    if (visible.isEmpty()) {
                        Text(stringResource(R.string.no_entries_period), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
                    }
                    visible.take(100).forEach { tx -> TransactionItem(tx, onClick = { onEdit(tx) }, showDate = true) }
                }
            }
        }

        if (editBudget) {
            BudgetDialog(
                current = cat.monthlyBudgetCents,
                onDismiss = { editBudget = false },
                onSave = { cents ->
                    vm.saveCategory(cat.copy(monthlyBudgetCents = cents))
                    editBudget = false
                },
            )
        }

        if (editCategory) {
            CategoryEditorSheet(
                category = cat,
                initialType = cat.type,
                existingTitles = allCategories.filter { it.type == cat.type }.map { it.title }.toSet(),
                onDismiss = { editCategory = false },
                onSave = {
                    vm.saveCategory(it)
                    editCategory = false
                },
                onDelete = {
                    vm.deleteCategory(cat)
                    editCategory = false
                    onBack()
                },
            )
        }
    }
}

/** Letos proti lani: vsota od 1. januarja do danes in po mesecih. */
@Composable
private fun YearComparisonCard(cmp: CategoryYearComparison, isExpense: Boolean) {
    val colors = Finance.colors
    // Pri stroških je več slabše (rdeče), pri prihodkih boljše (zeleno)
    fun deltaColor(pct: Int) = if ((pct > 0) == isExpense) colors.expense else colors.income
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.this_year_and_last), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.this_year_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCents(cmp.ytdCents), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.same_period_last_year), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCents(cmp.ytdLastYearCents), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cmp.deltaPct?.let { pct ->
            Text(
                stringResource(R.string.vs_last_year, (if (pct >= 0) "+" else "") + stringResource(R.string.percent, pct)),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = deltaColor(pct),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        cmp.months.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.label + if (i == 0) stringResource(R.string.to_date_suffix) else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatCents(m.thisYearCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.last_year_amount, formatCents(m.lastYearCents)),
                    style = MaterialTheme.typography.labelSmall,
                    color = percentDelta(m.thisYearCents, m.lastYearCents)?.takeIf { i > 0 }?.let(::deltaColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetCard(cat: CategoryEntity, spent: Long, onEdit: () -> Unit) {
    val colors = Finance.colors
    val budget = cat.monthlyBudgetCents
    SoftCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fraction = if (budget != null && budget > 0) spent.toFloat() / budget else 0f
            val c = when {
                fraction >= 1f -> colors.expense
                fraction >= 0.8f -> colors.warning
                else -> colors.income
            }
            ProgressRing(fraction, size = 64.dp, thickness = 7.dp, color = c) {
                Text(if (budget == null) "—" else stringResource(R.string.percent, (fraction * 100).roundToInt()), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.monthly_budget), style = MaterialTheme.typography.titleSmall)
                if (budget == null) {
                    Text(stringResource(R.string.budget_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val left = budget - spent
                    Text(
                        if (left >= 0) stringResource(R.string.budget_available, formatCents(left), formatCents(budget)) else stringResource(R.string.budget_exceeded_by, formatCents(-left)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else colors.expense,
                    )
                }
            }
            Text(stringResource(if (budget == null) R.string.set else R.string.edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BudgetDialog(current: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(current?.let(::centsToInput) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        title = { Text(stringResource(R.string.monthly_budget)) },
        text = {
            OutlinedTextField(
                text, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) text = v },
                label = { Text(stringResource(R.string.amount_eur)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.md),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(parseCents(text)?.takeIf { it > 0 }) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Seznam vseh kategorij (iz zaslona "Več"). */
@Composable
fun CategoriesScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var creating by remember { mutableStateOf(false) }
    val spent by vm.currentMonthByCategory.collectAsStateWithLifecycle()
    val overall by vm.overallBudget.collectAsStateWithLifecycle()
    val monthlyBudget by vm.monthlyBudget.collectAsStateWithLifecycle()
    var editOverall by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.categories), onBack)
        SegmentedTabs(
            listOf(TransactionType.EXPENSE, TransactionType.INCOME), type, { type = it },
            label = { str(if (it == TransactionType.EXPENSE) R.string.expenses_tab else R.string.income_plural) },
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        Spacer(Modifier.height(Spacing.lg))
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            if (type == TransactionType.EXPENSE) {
                item { OverallBudgetEditorCard(overall, onEdit = { editOverall = true }, Modifier.padding(bottom = Spacing.lg)) }
            }
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                    categories.filter { it.type == type }.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(c.uid) }.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(c.title, c.color, Modifier.sharedElementKey("cat-" + c.uid), size = 40.dp, type = c.type)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                c.monthlyBudgetCents?.let {
                                    Text(stringResource(R.string.budget_amount, formatCents(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(formatCents(spent[c.uid] ?: 0), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                PillButton(
                    stringResource(if (type == TransactionType.EXPENSE) R.string.new_expense_category else R.string.new_income_category),
                    Icons.Rounded.Add,
                    { creating = true },
                    Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    filled = true,
                )
            }
        }
    }

    if (editOverall) {
        OverallBudgetDialog(
            current = monthlyBudget,
            onDismiss = { editOverall = false },
            onSave = { vm.setMonthlyBudget(it); editOverall = false },
        )
    }

    if (creating) {
        CategoryEditorSheet(
            category = null,
            initialType = type,
            existingTitles = categories.filter { it.type == type }.map { it.title }.toSet(),
            onDismiss = { creating = false },
            onSave = { new ->
                vm.saveCategory(new.copy(position = (categories.maxOfOrNull { it.position } ?: 0) + 1))
                type = new.type
                creating = false
            },
        )
    }
}

/** Skupni mesečni proračun za vse stroške: napredek in napoved do konca meseca. */
@Composable
private fun OverallBudgetEditorCard(budget: OverallBudget?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Finance.colors
    SoftCard(modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.overall_monthly_budget), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    budget?.let { formatCents(it.budgetCents) } ?: stringResource(R.string.not_set),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(stringResource(if (budget == null) R.string.set else R.string.edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        if (budget != null) {
            val tint = when {
                budget.fraction >= 1f -> colors.expense
                budget.fraction >= 0.8f -> colors.warning
                else -> colors.income
            }
            Spacer(Modifier.height(Spacing.md))
            SlimProgress(budget.fraction, tint)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.budget_this_month, formatCents(budget.spentCents), stringResource(R.string.percent, (budget.fraction * 100).roundToInt())) +
                    if (budget.leftCents >= 0) stringResource(R.string.left_short, formatCents(budget.leftCents)) else stringResource(R.string.over_short, formatCents(-budget.leftCents)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.budget_forecast, formatCents(budget.forecastCents)),
                style = MaterialTheme.typography.bodySmall,
                color = if (budget.forecastCents > budget.budgetCents) colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.overall_budget_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverallBudgetDialog(current: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(current?.let(::centsToInput) ?: "") }
    val cents = parseCents(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.overall_monthly_budget)) },
        text = {
            OutlinedTextField(
                text, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) text = v },
                label = { Text(stringResource(R.string.amount_per_month)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.md),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(cents) }, enabled = cents != null) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
