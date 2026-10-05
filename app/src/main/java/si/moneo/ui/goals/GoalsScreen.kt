package si.moneo.ui.goals

import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import si.moneo.ui.fmt
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.ui.GoalUi
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.AnimatedAmount
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ProgressRing
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.sharedElementKey
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    vm: MainViewModel,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
    contentPadding: PaddingValues,
    /** Cilji so podzaslon zavihka Načrt - z gumbom nazaj. */
    onBack: () -> Unit,
) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val saved = goals.sumOf { it.savedCents }
    val target = goals.sumOf { it.goal.targetCents }

    Column(Modifier.fillMaxSize()) {
    ScreenTopBar(stringResource(R.string.savings_goals), onBack)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen, end = Spacing.screen,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(stringResource(R.string.goals_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(span = { GridItemSpan(2) }) {
            SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val progress = if (target > 0) saved.toFloat() / target else 0f
                    ProgressRing(progress, size = 88.dp, thickness = 9.dp) {
                        Text(stringResource(R.string.percent, (progress * 100).roundToInt()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column {
                        Text(stringResource(R.string.total_saved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedAmount(saved, style = MaterialTheme.typography.displaySmall)
                        Text(stringResource(R.string.of_amount_short, formatCents(target)) + " · " + pluralStringResource(R.plurals.goals_count, goals.size, goals.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (goals.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SoftCard(Modifier.fillMaxWidth()) {
                    EmptyState(Icons.Rounded.Flag, stringResource(R.string.no_goals_yet), stringResource(R.string.no_goals_long_hint))
                }
            }
        }
        items(goals, key = { it.goal.uid }) { g -> GoalCard(g, onClick = { onOpenGoal(g.goal.uid) }) }
        item(span = { GridItemSpan(2) }) {
            PillButton(stringResource(R.string.new_goal), Icons.Rounded.Add, onNewGoal, Modifier.fillMaxWidth(), filled = true)
        }
    }
    }
}

@Composable
private fun GoalCard(g: GoalUi, onClick: () -> Unit) {
    val accent = if (g.reached) Finance.colors.income else accentFor(g.goal.title, g.goal.color)
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        ProgressRing(g.progress, size = 72.dp, thickness = 6.dp, color = accent, modifier = Modifier.align(Alignment.CenterHorizontally).sharedElementKey("goal-" + g.goal.uid)) {
            Text(g.goal.emoji, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(g.goal.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatCents(g.savedCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(stringResource(R.string.of_amount_short, formatCents(g.goal.targetCents)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                g.reached -> stringResource(R.string.goal_reached)
                g.projectedDate != null -> stringResource(R.string.projected_short, g.projectedDate.fmt(R.string.fmt_month_year_short, "MMM yyyy"))
                else -> stringResource(R.string.percent, (g.progress * 100).roundToInt())
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
