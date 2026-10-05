package si.moneo.ui.plan

import si.moneo.R
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.subscriptions.hasEnded
import si.moneo.domain.subscriptions.monthlyCents
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.formatCents
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.monthOutlook
import si.moneo.ui.relativeDayLabel
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Zavihek "Načrt": vse, kar se tiče prihodnjega denarja - cilji varčevanja, naročnine,
 * ponavljajoča plačila, dolgovi in proračuni. Vsaka kartica pokaže glavno številko in odpre podroben zaslon.
 */
@Composable
fun PlanScreen(
    vm: MainViewModel,
    onOpenGoals: () -> Unit,
    onNewGoal: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenBudgets: () -> Unit,
    contentPadding: PaddingValues,
) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val rules by vm.activeRules.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val overallBudget by vm.overallBudget.collectAsStateWithLifecycle()
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    val colors = Finance.colors
    val today = LocalDate.now()
    val outlook = remember(subscriptions, rules) { monthOutlook(subscriptions, rules, today) }
    fun money(cents: Long) = if (hidden) "•••• €" else formatCents(cents)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = Spacing.md, bottom = Spacing.sm)) {
                Text(stringResource(R.string.plan), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.plan_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Povzetek: kaj se bo do konca meseca še samodejno zapisalo
        item {
            val owedToMe = debts.filter { !it.settled && it.direction == DebtDirection.LENT }.sumOf { it.remainingCents }
            val iOwe = debts.filter { !it.settled && it.direction == DebtDirection.BORROWED }.sumOf { it.remainingCents }
            SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(20.dp)) {
                Text(stringResource(R.string.plan_outgoing), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(outlook.outgoingCents), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.plan_outgoing_detail, money(outlook.subscriptionsCents), money(outlook.recurringExpenseCents)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (outlook.recurringIncomeCents > 0 || owedToMe > 0 || iOwe > 0) {
                    Spacer(Modifier.height(Spacing.md))
                    Row {
                        if (outlook.recurringIncomeCents > 0) {
                            Stat(stringResource(R.string.still_coming_in), money(outlook.recurringIncomeCents), colors.income, Modifier.weight(1f))
                        }
                        if (owedToMe > 0) Stat(stringResource(R.string.owed_to_you), money(owedToMe), colors.income, Modifier.weight(1f))
                        if (iOwe > 0) Stat(stringResource(R.string.you_owe), money(iOwe), colors.expense, Modifier.weight(1f))
                    }
                }
            }
        }

        // Cilji varčevanja
        item {
            val saved = goals.sumOf { it.savedCents }
            val target = goals.sumOf { it.goal.targetCents }
            val nearest = goals.filter { !it.reached }.maxByOrNull { it.progress }
            PlanCard(
                Icons.Rounded.Savings, colors.income, stringResource(R.string.savings_goals),
                value = if (goals.isEmpty()) stringResource(R.string.no_goals_yet) else stringResource(R.string.amount_of, money(saved), money(target)),
                detail = when {
                    goals.isEmpty() -> stringResource(R.string.goals_empty_hint)
                    nearest != null -> stringResource(
                        R.string.goals_nearest, pluralStringResource(R.plurals.goals_count, goals.size, goals.size),
                        nearest.goal.emoji, nearest.goal.title, stringResource(R.string.percent, (nearest.progress * 100).roundToInt()),
                    )
                    else -> stringResource(R.string.all_goals_reached)
                },
                progress = if (target > 0) saved.toFloat() / target else null,
                onClick = if (goals.isEmpty()) onNewGoal else onOpenGoals,
            )
        }

        // Naročnine
        item {
            val running = subscriptions.filter { it.active && !it.hasEnded }
            val next = running.minByOrNull { it.nextPaymentDate }
            PlanCard(
                Icons.Rounded.Subscriptions, colors.expense, stringResource(R.string.subscriptions),
                value = if (running.isEmpty()) stringResource(R.string.no_subscriptions_short) else stringResource(R.string.per_month_amount, money(running.sumOf { it.monthlyCents })),
                detail = when {
                    next != null -> stringResource(R.string.subs_next, running.size, next.title, relativeDayLabel(millisToLocalDate(next.nextPaymentDate)))
                    else -> stringResource(R.string.subs_plan_hint)
                },
                onClick = onOpenSubscriptions,
            )
        }

        // Ponavljajoča plačila
        item {
            val active = rules.filter { it.enabled }
            val next = active.minByOrNull { it.nextDueDate }
            PlanCard(
                Icons.Rounded.Autorenew, MaterialTheme.colorScheme.primary, stringResource(R.string.recurring_payments),
                value = if (active.isEmpty()) stringResource(R.string.no_rules) else pluralStringResource(R.plurals.rules_count_plural, active.size, active.size),
                detail = when {
                    next != null -> stringResource(
                        R.string.next_recurring, next.title,
                        (if (next.type == TransactionType.INCOME) "+" else "−") + money(next.amountCents),
                        relativeDayLabel(millisToLocalDate(next.nextDueDate)),
                    )
                    else -> stringResource(R.string.recurring_plan_hint)
                },
                onClick = onOpenRecurring,
            )
        }

        // Dolgovi
        item {
            val open = debts.filter { !it.settled }
            val overdue = open.count { d -> d.dueDate?.let { millisToLocalDate(it).isBefore(today) } == true }
            PlanCard(
                Icons.Rounded.Handshake, colors.warning, stringResource(R.string.debts),
                value = if (open.isEmpty()) stringResource(R.string.no_open_debts) else stringResource(R.string.open_count, open.size),
                detail = when {
                    open.isEmpty() -> stringResource(R.string.debts_plan_hint)
                    overdue > 0 -> stringResource(R.string.debts_overdue_check, pluralStringResource(R.plurals.overdue_count, overdue, overdue))
                    else -> stringResource(
                        R.string.debts_plan_summary,
                        money(open.filter { it.direction == DebtDirection.LENT }.sumOf { it.remainingCents }),
                        money(open.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.remainingCents }),
                    )
                },
                detailColor = if (overdue > 0) colors.expense else null,
                onClick = onOpenDebts,
            )
        }

        // Proračuni
        item {
            val withBudget = categories.count { (it.monthlyBudgetCents ?: 0) > 0 }
            val b = overallBudget
            PlanCard(
                Icons.Rounded.PieChart, colors.warning, stringResource(R.string.budgets),
                value = if (b != null) stringResource(R.string.amount_of, money(b.spentCents), money(b.budgetCents))
                else if (withBudget > 0) stringResource(R.string.categories_with_budget, pluralStringResource(R.plurals.categories_count, withBudget, withBudget))
                else stringResource(R.string.no_budgets),
                detail = when {
                    b != null -> stringResource(R.string.plan_budget_detail, stringResource(R.string.percent, (b.fraction * 100).roundToInt()), money(b.forecastCents))
                    withBudget > 0 -> stringResource(R.string.plan_set_overall_budget)
                    else -> stringResource(R.string.plan_budget_hint)
                },
                progress = b?.fraction,
                progressColor = when {
                    b == null -> null
                    b.fraction >= 1f -> colors.expense
                    b.fraction >= 0.8f -> colors.warning
                    else -> colors.income
                },
                onClick = onOpenBudgets,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun PlanCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    detail: String,
    onClick: () -> Unit,
    progress: Float? = null,
    progressColor: Color? = null,
    detailColor: Color? = null,
) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail, style = MaterialTheme.typography.bodySmall,
                    color = detailColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress != null) {
            Spacer(Modifier.height(Spacing.sm))
            SlimProgress(progress.coerceIn(0f, 1f), progressColor ?: tint)
        }
    }
}
