package si.moneo.ui.goals

import si.moneo.ui.theme.asGraphic
import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmt
import si.moneo.ui.fmtDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.AnimatedAmount
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.NumPad
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ProgressRing
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.sharedElementKey
import si.moneo.ui.components.prettyExpression
import si.moneo.ui.evaluateAmountExpression
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale


@Composable
fun GoalDetailScreen(vm: MainViewModel, goalUid: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val roundUpGoal by vm.roundUpGoalUid.collectAsStateWithLifecycle()
    val g = goals.firstOrNull { it.goal.uid == goalUid }
    var amountMode by remember { mutableStateOf<Boolean?>(null) } // true = vplačilo, false = dvig

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.goal), onBack) {
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Settings, stringResource(R.string.edit_goal)) }
        }
        if (g == null) return@Column
        val accent = if (g.reached) Finance.colors.income else accentFor(g.goal.title, g.goal.color)
        val grouped = remember(g.contributions) { g.contributions.groupBy { millisToLocalDate(it.date) } }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(Spacing.md))
                    ProgressRing(g.progress, size = 180.dp, thickness = 12.dp, color = accent.asGraphic(), modifier = Modifier.sharedElementKey("goal-" + g.goal.uid)) {
                        Text(g.goal.emoji, style = MaterialTheme.typography.displayLarge)
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Text(g.goal.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(g.savedCents, style = MaterialTheme.typography.displayMedium)
                    Text(stringResource(R.string.of_amount_short, formatCents(g.goal.targetCents)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        PillButton(stringResource(R.string.deposit), Icons.Rounded.Download, { amountMode = true }, Modifier.weight(1f))
                        PillButton(stringResource(R.string.withdraw), Icons.Rounded.Upload, { amountMode = false }, Modifier.weight(1f), enabled = g.savedCents > 0)
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    RoundUpToggle(
                        enabled = roundUpGoal == goalUid,
                        otherGoal = goals.firstOrNull { it.goal.uid == roundUpGoal && roundUpGoal != goalUid }?.goal?.title,
                        onChange = { vm.setRoundUpGoal(if (it) goalUid else null) },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    GoalFacts(g.goal.deadline, g.projectedDate, g.goal.targetCents - g.savedCents)
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(top = 10.dp)) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.outline),
                        )
                        if (g.contributions.isEmpty()) {
                            EmptyState(Icons.Rounded.History, stringResource(R.string.no_deposits), stringResource(R.string.first_deposit_hint))
                        }
                    }
                }
            }
            grouped.forEach { (date, list) ->
                item(key = "d-$date") {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            dayLabel(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                items(list, key = { it.uid }) { c -> ContributionRow(c) }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().height(40.dp).navigationBarsPadding()) {}
            }
        }

        amountMode?.let { deposit ->
            AmountSheet(
                title = stringResource(if (deposit) R.string.deposit_into else R.string.withdraw_from, g.goal.title),
                maxCents = if (deposit) null else g.savedCents,
                onDismiss = { amountMode = null },
                onConfirm = { cents, note ->
                    vm.contribute(goalUid, if (deposit) cents else -cents, note)
                    amountMode = null
                },
            )
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> str(R.string.today)
        today.minusDays(1) -> str(R.string.yesterday)
        else -> date.fmt(R.string.fmt_date_long, "d. MMMM yyyy")
    }
}

@Composable
private fun GoalFacts(deadline: Long?, projected: LocalDate?, remainingCents: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        if (remainingCents > 0) {
            if (deadline != null) {
                val d = millisToLocalDate(deadline)
                val months = ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), d.withDayOfMonth(1)).coerceAtLeast(1)
                Fact(Icons.Rounded.Event, stringResource(R.string.deadline_on, d.fmtDate()), stringResource(R.string.deadline_monthly, formatCents(remainingCents / months)))
            }
            if (projected != null) {
                Fact(Icons.AutoMirrored.Rounded.TrendingFlat, stringResource(R.string.projected_on, projected.fmtDate()), stringResource(R.string.projected_hint))
            }
        }
    }
}

/** Zaokroževanje stroškov navzgor na cel evro - razlika gre v ta cilj. */
@Composable
private fun RoundUpToggle(enabled: Boolean, otherGoal: String?, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Savings, Finance.colors.income, size = 36.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.roundup_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        enabled -> stringResource(R.string.roundup_on)
                        otherGoal != null -> stringResource(R.string.roundup_other, otherGoal)
                        else -> stringResource(R.string.roundup_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.primary, size = 36.dp)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContributionRow(c: GoalContributionEntity) {
    val deposit = c.amountCents >= 0
    val colors = Finance.colors
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (deposit) Icons.Rounded.Download else Icons.Rounded.Upload, if (deposit) colors.income else colors.expense, size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(c.note.ifBlank { stringResource(if (deposit) R.string.deposit_noun else R.string.withdrawal_noun) }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(if (deposit) R.string.deposit_noun else R.string.partial_withdrawal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                (if (deposit) "+" else "−") + formatCents(kotlin.math.abs(c.amountCents)),
                fontWeight = FontWeight.Bold,
                color = if (deposit) colors.income else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Sheet z numerično tipkovnico za vplačilo/dvig. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountSheet(
    title: String,
    maxCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    var expr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cents = evaluateAmountExpression(expr.replace('−', '-'))?.coerceAtLeast(0) ?: 0
    val valid = cents > 0 && (maxCents == null || cents <= maxCents)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                (if (expr.isEmpty()) "0" else prettyExpression(expr)) + " €",
                style = MaterialTheme.typography.displayMedium,
                color = if (valid || expr.isEmpty()) MaterialTheme.colorScheme.onSurface else Finance.colors.expense,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (maxCents != null) {
                Text(
                    stringResource(R.string.available_amount, formatCents(maxCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            OutlinedTextField(
                note, { note = it },
                placeholder = { Text(stringResource(R.string.note_optional)) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )
            NumPad(expr, { expr = it })
            PillButton(stringResource(R.string.confirm), null, { onConfirm(cents, note.trim()) }, Modifier.fillMaxWidth().height(56.dp), filled = true, enabled = valid)
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
