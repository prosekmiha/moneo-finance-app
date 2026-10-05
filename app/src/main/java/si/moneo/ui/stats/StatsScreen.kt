package si.moneo.ui.stats

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.moneo.data.report.PdfReport
import si.moneo.ui.components.LocalSnackbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.categoryKey
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.ui.CategorySpend
import si.moneo.ui.Insight
import si.moneo.ui.InsightKind
import si.moneo.ui.MainViewModel
import si.moneo.ui.UNCATEGORIZED
import si.moneo.ui.components.AccountFilterRow
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.DonutChart
import si.moneo.ui.components.DonutSlice
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.IncomeExpenseBarChart
import si.moneo.ui.components.MoneyInOutTile
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.sharedElementKey
import si.moneo.ui.formatCents
import si.moneo.ui.percentDelta
import si.moneo.ui.savingsRate
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    vm: MainViewModel,
    onOpenCategory: (String) -> Unit,
    onOpenYearReview: () -> Unit,
    onOpenTags: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by vm.stats.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val accountFilter by vm.accountFilter.collectAsStateWithLifecycle()
    val colors = Finance.colors
    val bucket = state.selected
    val prev = state.previous
    // Kategorije: odhodki ali prihodki (ob vrnitvi vedno odhodki)
    var spendType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedSlice by remember(state.selectedIndex, state.period, spendType) { mutableStateOf<Int?>(null) }
    val isExpense = spendType == TransactionType.EXPENSE
    val spends = if (isExpense) state.spends else state.incomeSpends
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var exporting by remember { mutableStateOf(false) }

    /** PDF poročilo izbranega obdobja -> deljenje (e-pošta, Drive, Viber ...). */
    fun exportPdf() {
        val report = vm.periodReport() ?: return
        exporting = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { PdfReport.write(context, report) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, str(R.string.pdf_subject, report.periodLabel))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, str(R.string.share_report)))
            } catch (e: Exception) {
                snackbar.showSnackbar(str(R.string.report_failed, e.message ?: e::class.simpleName.orEmpty()))
            } finally {
                exporting = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen, end = Spacing.screen,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.statistics), style = MaterialTheme.typography.headlineSmall)
                    AnimatedContent(bucket?.longLabel ?: "", label = "period") { label ->
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = ::exportPdf, enabled = !exporting) {
                    Icon(Icons.Rounded.PictureAsPdf, stringResource(R.string.export_pdf), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // Pregledi: leto v številkah in poraba po oznakah
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ReportButton(Icons.Rounded.Celebration, stringResource(R.string.year_review), onOpenYearReview, Modifier.weight(1f))
                ReportButton(Icons.AutoMirrored.Rounded.Label, stringResource(R.string.by_tags), onOpenTags, Modifier.weight(1f))
            }
        }
        if (accounts.size > 1) {
            item {
                AccountFilterRow(accounts, accountFilter, { vm.accountFilter.value = it }, contentPadding = PaddingValues(0.dp))
            }
        }
        item {
            SegmentedTabs(
                options = si.moneo.ui.STATS_PERIODS,
                selected = state.period,
                onSelect = vm::setStatsPeriod,
                label = { it.label },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MoneyInOutTile(
                    stringResource(R.string.income_plural), bucket?.incomeCents ?: 0, income = true, Modifier.weight(1f),
                    deltaPct = prev?.let { percentDelta(bucket?.incomeCents ?: 0, it.incomeCents) },
                )
                MoneyInOutTile(
                    stringResource(R.string.expenses), bucket?.expenseCents ?: 0, income = false, Modifier.weight(1f),
                    deltaPct = prev?.let { percentDelta(bucket?.expenseCents ?: 0, it.expenseCents) },
                )
            }
        }
        state.lastYear?.let { ly ->
            item {
                // Primerjava z istim obdobjem lani (pri tekočem obdobju do istega dne)
                fun delta(cur: Long, prev: Long) = percentDelta(cur, prev)?.let { " (" + (if (it >= 0) "+" else "") + str(R.string.percent, it) + ")" }.orEmpty()
                Text(
                    stringResource(
                        R.string.last_year_same_period,
                        formatCents(ly.incomeCents), delta(bucket?.incomeCents ?: 0, ly.incomeCents),
                        formatCents(ly.expenseCents), delta(bucket?.expenseCents ?: 0, ly.expenseCents),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.lg, horizontal = Spacing.md)) {
                IncomeExpenseBarChart(state.buckets, state.selectedIndex, vm::selectBucket)
                Spacer(Modifier.height(Spacing.md))
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(stringResource(R.string.income_plural), colors.income)
                    Spacer(Modifier.width(Spacing.lg))
                    Legend(stringResource(R.string.expenses), colors.expense)
                    Spacer(Modifier.weight(1f))
                    bucket?.let { savingsRate(it) }?.let { rate ->
                        Text(
                            stringResource(R.string.savings_rate, stringResource(R.string.percent, rate)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (rate >= 0) colors.income else colors.expense,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (state.insights.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.insights)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(state.insights) { InsightCard(it) }
                }
            }
        }

        item {
            SegmentedTabs(
                listOf(TransactionType.EXPENSE, TransactionType.INCOME), spendType, { spendType = it },
                label = { str(if (it == TransactionType.EXPENSE) R.string.expenses_by_category else R.string.income_by_category) },
                selectedColor = if (isExpense) colors.expense else colors.income,
                onSelectedColor = androidx.compose.ui.graphics.Color.White,
            )
        }
        if (spends.isEmpty()) {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        Icons.Rounded.PieChart,
                        stringResource(if (isExpense) R.string.no_expenses_in_period else R.string.no_income_in_period),
                        stringResource(R.string.pick_other_period),
                    )
                }
            }
        } else {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    val slices = spends.map { DonutSlice(it.totalCents.toFloat(), accentFor(it.category.title, it.category.color)) }
                    val focus = selectedSlice?.let { spends.getOrNull(it) }
                    DonutChart(
                        slices, selectedSlice, onSelect = { selectedSlice = it },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        description = str(if (isExpense) R.string.expenses_by_category else R.string.income_by_category) + ": " + spends.joinToString {
                            "${it.category.title} ${formatCents(it.totalCents)}, " + str(R.string.percent, (it.share * 100).roundToInt())
                        },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                focus?.category?.title ?: stringResource(R.string.total),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(110.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                formatCents(focus?.totalCents ?: (if (isExpense) bucket?.expenseCents else bucket?.incomeCents) ?: 0),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            )
                            if (focus != null) {
                                Text(stringResource(R.string.percent, (focus.share * 100).roundToInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    TopThree(spends.take(3), onOpenCategory)
                }
            }
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                    val budgets = if (isExpense) state.budgets.associateBy { it.category.uid } else emptyMap()
                    spends.forEachIndexed { i, s ->
                        CategoryRow(
                            s,
                            lastYearCents = state.lastYearByCategory[categoryKey(spendType, s.category.uid)],
                            budgetCents = budgets[s.category.uid]?.budgetCents,
                            highlighted = selectedSlice == i,
                            onClick = { if (s.category !== UNCATEGORIZED) onOpenCategory(s.category.uid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val colors = Finance.colors
    val (icon, tint) = insightStyle(insight.kind)
    SoftCard(Modifier.width(260.dp), color = if (insight.kind == InsightKind.TREND_UP) colors.warningBg else MaterialTheme.colorScheme.surface) {
        IconBadge(icon, tint, size = 36.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text(insight.text, style = MaterialTheme.typography.bodyMedium, minLines = 3)
    }
}

@Composable
private fun insightStyle(kind: InsightKind): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    val c = Finance.colors
    return when (kind) {
        InsightKind.TOP_CATEGORY -> Icons.Rounded.Lightbulb to c.warning
        InsightKind.TREND_UP -> Icons.AutoMirrored.Rounded.TrendingUp to c.expense
        InsightKind.TREND_DOWN -> Icons.AutoMirrored.Rounded.TrendingDown to c.income
        InsightKind.BIG_EXPENSE -> Icons.Rounded.Star to c.expense
        InsightKind.FORECAST -> Icons.Rounded.Schedule to MaterialTheme.colorScheme.primary
        InsightKind.SAVINGS -> Icons.Rounded.Savings to c.income
        InsightKind.BUDGET -> Icons.Rounded.WarningAmber to c.warning
    }
}

@Composable
private fun TopThree(top: List<CategorySpend>, onOpenCategory: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        top.forEach { s ->
            SoftCard(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = { if (s.category !== UNCATEGORIZED) onOpenCategory(s.category.uid) },
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
            ) {
                CategoryIcon(s.category.title, s.category.color, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    formatCents(s.totalCents),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    maxLines = 1,
                )
            }
        }
        repeat(3 - top.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun CategoryRow(s: CategorySpend, lastYearCents: Long?, budgetCents: Long?, highlighted: Boolean, onClick: () -> Unit) {
    val accent = accentFor(s.category.title, s.category.color)
    val bg = if (highlighted) accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(s.category.title, s.category.color, Modifier.sharedElementKey("cat-" + s.category.uid), size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.category.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatCents(s.totalCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            val fraction = if (budgetCents != null && budgetCents > 0) s.totalCents.toFloat() / budgetCents else s.share
            SlimProgress(
                fraction,
                if (budgetCents != null && fraction >= 1f) Finance.colors.expense else accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                (if (budgetCents != null) stringResource(R.string.pct_of_budget, stringResource(R.string.percent, (fraction * 100).roundToInt()), formatCents(budgetCents))
                else stringResource(R.string.percent, (s.share * 100).roundToInt()) + " · " + pluralStringResource(R.plurals.entries_count, s.count, s.count)) +
                    (lastYearCents?.takeIf { it > 0 }?.let { ly ->
                        str(R.string.last_year_short, formatCents(ly)) + percentDelta(s.totalCents, ly)?.let { " (" + (if (it >= 0) "+" else "") + str(R.string.percent, it) + ")" }.orEmpty()
                    }.orEmpty()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

