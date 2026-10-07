package si.moneo.ui.search

import si.moneo.ui.theme.Radius
import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import si.moneo.ui.components.DateField
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.centsToInput
import si.moneo.ui.parseCents
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.MainViewModel
import si.moneo.ui.SearchFilter
import si.moneo.ui.TransactionUi
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.DayHeader
import si.moneo.ui.components.TransactionItem
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(vm: MainViewModel, onBack: () -> Unit, onEdit: (TransactionUi) -> Unit) {
    val filter by vm.searchFilter.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { vm.searchFilter.value = SearchFilter() } }

    fun update(f: SearchFilter) { vm.searchFilter.value = f }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            TextField(
                value = filter.query,
                onValueChange = { update(filter.copy(query = it)) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { update(filter.copy(query = "")) }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                    }
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                FilterChip(
                    selected = filter.advancedCount > 0 || showFilters,
                    onClick = { showFilters = !showFilters },
                    label = { Text(if (filter.advancedCount > 0) stringResource(R.string.filters_count, filter.advancedCount) else stringResource(R.string.filters)) },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp)) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    border = null,
                )
            }
            item {
                Chip(stringResource(R.string.expenses), filter.type == TransactionType.EXPENSE) {
                    update(filter.copy(type = if (filter.type == TransactionType.EXPENSE) null else TransactionType.EXPENSE))
                }
            }
            item {
                Chip(stringResource(R.string.income_plural), filter.type == TransactionType.INCOME) {
                    update(filter.copy(type = if (filter.type == TransactionType.INCOME) null else TransactionType.INCOME))
                }
            }
            items(categories.filter { filter.type == null || it.type == filter.type }, key = { it.uid }) { c ->
                Chip(c.title, filter.categoryUid == c.uid) {
                    update(filter.copy(categoryUid = if (filter.categoryUid == c.uid) null else c.uid))
                }
            }
        }
        AnimatedVisibility(showFilters) {
            AdvancedFilters(
                filter, accounts, tags.map { it.tag },
                onChange = ::update,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md),
            )
        }
        Spacer(Modifier.height(Spacing.md))

        LazyColumn(contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp)) {
            if (!filter.isActive) {
                item { EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_hint)) }
            } else if (results.total == 0) {
                item { EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.no_results), stringResource(R.string.no_results_hint)) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm, start = 4.dp, end = 4.dp)) {
                        Text(pluralStringResource(R.plurals.results_count, results.total, results.total), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (results.incomeCents > 0) Text("+${formatCents(results.incomeCents)}  ", color = Finance.colors.income, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        if (results.expenseCents > 0) Text("−${formatCents(results.expenseCents)}", color = Finance.colors.expense, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                // Rezultati po dnevih (najnovejši najprej); glava dneva pokaže neto vsoto potrjenih
                results.items.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (date, list) ->
                    stickyHeader(key = "day-$date") {
                        val net = list.filter { it.confirmed }.sumOf { if (it.type == TransactionType.INCOME) it.amountCents else -it.amountCents }
                        DayHeader(
                            date,
                            amount = (if (net >= 0) "+" else "−") + formatCents(kotlin.math.abs(net)),
                            amountColor = if (net > 0) Finance.colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                            color = MaterialTheme.colorScheme.background,
                            horizontalPadding = 4.dp,
                        )
                    }
                    items(list, key = { it.uid }) { tx ->
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            TransactionItem(tx, onClick = { onEdit(tx) })
                        }
                    }
                }
                if (results.total > results.items.size) {
                    item {
                        Text(
                            stringResource(R.string.results_limited, results.items.size, results.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = Finance.colors.selectedTab,
            selectedLabelColor = Finance.colors.onSelectedTab,
        ),
        border = null,
    )
}

/** Napredni filtri: obdobje (s hitrimi izbirami), razpon zneska, račun, oznaka. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilters(
    filter: SearchFilter,
    accounts: List<si.moneo.data.db.entity.AccountEntity>,
    tags: List<String>,
    onChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    var minText by remember(filter.minCents == null) { mutableStateOf(filter.minCents?.let(::centsToInput).orEmpty()) }
    var maxText by remember(filter.maxCents == null) { mutableStateOf(filter.maxCents?.let(::centsToInput).orEmpty()) }
    fun amountOk(v: String) = v.all { it.isDigit() || it == ',' || it == '.' }
    SoftCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.period), style = MaterialTheme.typography.labelLarge)
            val month = YearMonth.from(today)
            val presets = listOf(
                stringResource(R.string.this_month) to (month.atDay(1) to today),
                stringResource(R.string.last_month) to (month.minusMonths(1).atDay(1) to month.minusMonths(1).atEndOfMonth()),
                stringResource(R.string.last_30_days) to (today.minusDays(29) to today),
                stringResource(R.string.this_year) to (today.withDayOfYear(1) to today),
                stringResource(R.string.last_year) to (today.minusYears(1).withDayOfYear(1) to today.withDayOfYear(1).minusDays(1)),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (label, range) ->
                    val selected = filter.from == range.first && filter.to == range.second
                    Chip(label, selected) {
                        onChange(if (selected) filter.copy(from = null, to = null) else filter.copy(from = range.first, to = range.second))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DateField("Od", filter.from, { onChange(filter.copy(from = it)) }, Modifier.weight(1f), clearable = true)
                DateField("Do", filter.to, { onChange(filter.copy(to = it)) }, Modifier.weight(1f), clearable = true)
            }
            Text(stringResource(R.string.amount), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    minText,
                    { v -> if (amountOk(v)) { minText = v; onChange(filter.copy(minCents = parseCents(v)?.takeIf { v.isNotBlank() })) } },
                    label = { Text(stringResource(R.string.min_eur)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(Radius.md), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    maxText,
                    { v -> if (amountOk(v)) { maxText = v; onChange(filter.copy(maxCents = parseCents(v)?.takeIf { v.isNotBlank() })) } },
                    label = { Text(stringResource(R.string.max_eur)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(Radius.md), modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (accounts.size > 1) {
                    DropdownField(
                        stringResource(R.string.account), listOf<Pair<String?, String>>(null to stringResource(R.string.all_masc)) + accounts.map { it.uid to it.title },
                        filter.accountUid, { onChange(filter.copy(accountUid = it)) }, Modifier.weight(1f),
                    )
                }
                if (tags.isNotEmpty()) {
                    DropdownField(
                        stringResource(R.string.tag), listOf<Pair<String?, String>>(null to stringResource(R.string.all_fem)) + tags.map { it to "#$it" },
                        filter.tag, { onChange(filter.copy(tag = it)) }, Modifier.weight(1f),
                    )
                }
            }
            if (filter.advancedCount > 0) {
                TextButton(onClick = {
                    minText = ""
                    maxText = ""
                    onChange(filter.copy(from = null, to = null, minCents = null, maxCents = null, accountUid = null, tag = null))
                }) { Text(stringResource(R.string.clear_filters)) }
            }
        }
    }
}
