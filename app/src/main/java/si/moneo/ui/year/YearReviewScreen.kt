package si.moneo.ui.year

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import si.moneo.ui.fmtDayMonth
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.moneo.ui.MainViewModel
import si.moneo.ui.MonthTotal
import si.moneo.ui.YearReview
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.IncomeExpenseBarChart
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


/** Pregled leta: prihodki, stroški, privarčevano, najdražji meseci in kategorije. */
@Composable
fun YearReviewScreen(vm: MainViewModel, initialYear: Int, onBack: () -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(initialYear) }
    val flow = remember(year) { vm.yearReview(year) }
    val r by flow.collectAsState(YearReview(year))
    val colors = Finance.colors
    val thisYear = LocalDate.now().year
    val oldest = r.availableYears.minOrNull() ?: thisYear
    var selectedMonth by remember(year) { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.year_review), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { year-- }, enabled = year > oldest) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_year)) }
                    Text("$year", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = Spacing.md))
                    IconButton(onClick = { year++ }, enabled = year < thisYear) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_year)) }
                }
            }
            if (!r.hasData) {
                item { EmptyState(Icons.Rounded.CalendarMonth, stringResource(R.string.year_no_entries, year), stringResource(R.string.year_pick_other)) }
                return@LazyColumn
            }
            item {
                SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(20.dp)) {
                    Text(
                        if (r.netCents >= 0) stringResource(R.string.saved_in_year, year) else stringResource(R.string.spent_more_than_received),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(formatCents(abs(r.netCents)), style = MaterialTheme.typography.displayMedium, color = if (r.netCents >= 0) colors.income else colors.expense)
                    Spacer(Modifier.height(Spacing.md))
                    Row {
                        Stat(stringResource(R.string.income_plural), formatCents(r.incomeCents), colors.income, Modifier.weight(1f))
                        Stat(stringResource(R.string.expenses), formatCents(r.expenseCents), colors.expense, Modifier.weight(1f))
                        r.savingsRate?.let { Stat(stringResource(R.string.savings), stringResource(R.string.percent, it), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) }
                    }
                }
            }
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.lg, horizontal = Spacing.md)) {
                    IncomeExpenseBarChart(r.months, selectedMonth, { selectedMonth = if (selectedMonth == it) -1 else it }, height = 180.dp)
                    val sel = r.months.getOrNull(selectedMonth)
                    Text(
                        if (sel == null) stringResource(R.string.tap_month_details)
                        else stringResource(R.string.month_in_out, sel.longLabel, formatCents(sel.incomeCents), formatCents(sel.expenseCents)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = Spacing.sm),
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.highlights)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Highlight(Icons.AutoMirrored.Rounded.TrendingUp, colors.expense, stringResource(R.string.most_expensive_month), r.mostExpensiveMonth?.let(::monthLine), Modifier.weight(1f))
                        Highlight(Icons.AutoMirrored.Rounded.TrendingDown, colors.income, stringResource(R.string.cheapest_month), r.cheapestMonth?.let(::monthLine), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Highlight(
                            Icons.Rounded.Star, colors.warning, stringResource(R.string.biggest_expense),
                            r.biggestExpense?.let { "${it.title}\n${formatCents(it.amountCents)} · ${it.date.fmtDayMonth()}" },
                            Modifier.weight(1f),
                        )
                        Highlight(Icons.Rounded.Today, MaterialTheme.colorScheme.primary, stringResource(R.string.average_per_day), formatCents(r.averagePerDayCents) + "\n" + stringResource(R.string.of_expenses_suffix), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Highlight(
                            Icons.Rounded.Subscriptions, colors.expense, stringResource(R.string.subscriptions),
                            if (r.subscriptionsCents > 0) formatCents(r.subscriptionsCents) + "\n" + stringResource(R.string.pct_of_expenses, stringResource(R.string.percent, percentOf(r.subscriptionsCents, r.expenseCents))) else null,
                            Modifier.weight(1f),
                        )
                        Highlight(
                            Icons.Rounded.Celebration, if ((r.expenseDeltaPct ?: 0) > 0) colors.expense else colors.income, stringResource(R.string.compared_with, year - 1),
                            r.expenseDeltaPct?.let { d ->
                                when {
                                    d > 0 -> str(R.string.expenses_higher, str(R.string.percent, d))
                                    d < 0 -> str(R.string.expenses_lower, str(R.string.percent, -d))
                                    else -> str(R.string.expenses_same)
                                } + if (year == thisYear) "\n" + str(R.string.same_period_ly_short) else ""
                            },
                            Modifier.weight(1f),
                        )
                    }
                    r.topTag?.let { t ->
                        Highlight(
                            Icons.AutoMirrored.Rounded.Label, accentFor(t.tag), stringResource(R.string.top_tag),
                            "${t.tag} · ${formatCents(t.expenseCents)}", Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (r.topCategories.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.most_spent_on)) }
                item {
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                        r.topCategories.forEachIndexed { i, s ->
                            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ordinal_rank, i + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                                CategoryIcon(s.category.title, s.category.color, size = 36.dp)
                                Spacer(Modifier.width(Spacing.md))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(s.category.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatCents(s.totalCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    SlimProgress(s.share, accentFor(s.category.title, s.category.color))
                                    Text(
                                        stringResource(R.string.per_month_suffix, stringResource(R.string.percent, (s.share * 100).roundToInt()), formatCents(s.totalCents / r.monthTotals.size.coerceAtLeast(1))),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (r.topIncome.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.top_income)) }
                item {
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                        r.topIncome.forEach { s ->
                            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(s.category.title, s.category.color, size = 36.dp)
                                Spacer(Modifier.width(Spacing.md))
                                Text(s.category.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(formatCents(s.totalCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.income)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.year_entries, pluralStringResource(R.plurals.entries_count, r.transactionCount, r.transactionCount), year) + if (year == thisYear) stringResource(R.string.to_date_paren) else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
private fun Highlight(icon: ImageVector, tint: Color, title: String, value: String?, modifier: Modifier = Modifier) {
    SoftCard(modifier, contentPadding = PaddingValues(14.dp)) {
        IconBadge(icon, tint, size = 32.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun monthLine(m: MonthTotal): String =
    m.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.uppercase() } + "\n" + formatCents(m.expenseCents)

private fun percentOf(part: Long, total: Long): Int = if (total <= 0) 0 else ((part.toDouble() / total) * 100).roundToInt()
