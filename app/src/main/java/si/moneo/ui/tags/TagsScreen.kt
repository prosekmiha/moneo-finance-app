package si.moneo.ui.tags

import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.qty
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.ui.MainViewModel
import si.moneo.ui.TagDetailState
import si.moneo.ui.TagSummary
import si.moneo.ui.TransactionUi
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.TransactionItem
import si.moneo.ui.components.shortDate
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import kotlin.math.roundToInt

/** Seznam vseh oznak s skupnimi zneski. */
@Composable
fun TagsScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.tags), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    stringResource(R.string.tags_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tags.isEmpty()) {
                item { EmptyState(Icons.AutoMirrored.Rounded.Label, stringResource(R.string.no_tags), stringResource(R.string.no_tags_hint)) }
            }
            items(tags, key = { it.tag.lowercase() }) { t -> TagRow(t) { onOpen(t.tag) } }
        }
    }
}

@Composable
private fun TagRow(t: TagSummary, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.AutoMirrored.Rounded.Label, accentFor(t.tag), size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(t.tag, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.entries_count, t.count, t.count) + " · " +
                        if (t.first == t.last) shortDate(t.first) else stringResource(R.string.date_range, shortDate(t.first), shortDate(t.last)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCents(t.expenseCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (t.incomeCents > 0) {
                    Text("+${formatCents(t.incomeCents)}", style = MaterialTheme.typography.labelSmall, color = Finance.colors.income)
                }
            }
        }
    }
}

/** Vse transakcije z oznako, skupni znesek in razdelitev po kategorijah. */
@Composable
fun TagDetailScreen(vm: MainViewModel, tag: String, onBack: () -> Unit, onOpenTransaction: (TransactionUi) -> Unit) {
    val flow = remember(tag) { vm.tagDetail(tag) }
    val state by flow.collectAsState(TagDetailState(tag))
    val summary = state.summary
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(tag, onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    Text(stringResource(R.string.total_spent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCents(summary?.expenseCents ?: 0), style = MaterialTheme.typography.displaySmall)
                    if (summary != null) {
                        Text(
                            buildList {
                                add(qty(R.plurals.entries_count, summary.count, summary.count))
                                if (summary.incomeCents > 0) add(str(R.string.income_amount, formatCents(summary.incomeCents)))
                                add(if (summary.first == summary.last) shortDate(summary.first) else str(R.string.date_range, shortDate(summary.first), shortDate(summary.last)))
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.byCategory.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.by_category)) }
                item {
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                        state.byCategory.forEach { s ->
                            val accent = accentFor(s.category.title, s.category.color)
                            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(s.category.title, s.category.color, size = 36.dp)
                                Spacer(Modifier.width(Spacing.md))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(s.category.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(formatCents(s.totalCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    SlimProgress(s.share, accent)
                                    Text(stringResource(R.string.percent, (s.share * 100).roundToInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (state.transactions.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.entries)) }
                item {
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        state.transactions.forEach { tx -> TransactionItem(tx, onClick = { onOpenTransaction(tx) }, showDate = true) }
                    }
                }
            }
        }
    }
}
