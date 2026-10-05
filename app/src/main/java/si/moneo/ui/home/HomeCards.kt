package si.moneo.ui.home

import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.OverallBudget
import si.moneo.ui.UpcomingKind
import si.moneo.ui.UpcomingPayment
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.IconBadge
import si.moneo.ui.components.SectionHeader
import si.moneo.ui.components.SlimProgress
import si.moneo.ui.components.SoftCard
import si.moneo.ui.formatCents
import si.moneo.ui.relativeDayLabel
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.roundToInt

/** "V naslednjih 7 dneh": naročnine, ponavljajoča plačila in roki dolgov. */
@Composable
fun UpcomingCard(
    items: List<UpcomingPayment>,
    categories: List<CategoryEntity>,
    hidden: Boolean,
    onOpen: (UpcomingPayment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Finance.colors
    SoftCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.md)) {
        SectionHeader(stringResource(R.string.next_7_days), Modifier.padding(horizontal = Spacing.lg))
        val outgoing = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        if (outgoing > 0) {
            Text(
                stringResource(R.string.outgoing_amount, if (hidden) "•••• €" else formatCents(outgoing)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        items.take(5).forEach { p ->
            val cat = categories.firstOrNull { it.uid == p.categoryUid }
            val overdue = p.date.isBefore(LocalDate.now())
            SoftCard(
                Modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color.Transparent,
                onClick = { onOpen(p) }, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.kind == UpcomingKind.DEBT_I_OWE || p.kind == UpcomingKind.DEBT_OWED_TO_ME) {
                        IconBadge(Icons.Rounded.Handshake, if (p.kind == UpcomingKind.DEBT_I_OWE) colors.expense else colors.income, size = 36.dp)
                    } else {
                        CategoryIcon(cat?.title ?: p.title, cat?.color, type = p.type, size = 36.dp)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (p.kind) {
                                UpcomingKind.SUBSCRIPTION -> stringResource(R.string.kind_subscription)
                                UpcomingKind.RECURRING -> stringResource(R.string.kind_recurring)
                                UpcomingKind.DEBT_I_OWE -> stringResource(R.string.kind_debt_i_owe)
                                UpcomingKind.DEBT_OWED_TO_ME -> stringResource(R.string.kind_debt_owed)
                            } + " · " + relativeDayLabel(p.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (hidden) "•••• €" else (if (p.type == TransactionType.INCOME) "+" else "−") + formatCents(p.amountCents),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        color = if (p.type == TransactionType.INCOME) colors.income else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (items.size > 5) {
            Text(
                stringResource(R.string.and_n_more, items.size - 5),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
    }
}

/** Opozorilo skupnega mesečnega proračuna (od 80 % naprej). */
@Composable
fun OverallBudgetCard(budget: OverallBudget, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Finance.colors
    val over = budget.fraction >= 1f
    val tint = if (over) colors.expense else colors.warning
    SoftCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Savings, tint, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (over) stringResource(R.string.monthly_budget_exceeded)
                    else stringResource(R.string.monthly_budget_used, (budget.fraction * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                SlimProgress(budget.fraction, tint)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.amount_of, formatCents(budget.spentCents), formatCents(budget.budgetCents)) +
                        if (over) stringResource(R.string.over_by, formatCents(-budget.leftCents)) else stringResource(R.string.left_amount, formatCents(budget.leftCents)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ob koncu/začetku leta povabi k letnemu pregledu. */
@Composable
fun YearReviewPromo(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth(), onClick = onClick, color = MaterialTheme.colorScheme.primaryContainer, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Celebration, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.year_in_numbers, year), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.year_promo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Leto za letni pregled na Domov: od 15. decembra tekoče, v januarju prejšnje; sicer null. */
fun yearReviewPromoYear(today: LocalDate = LocalDate.now()): Int? = when {
    today.monthValue == 12 && today.dayOfMonth >= 15 -> today.year
    today.monthValue == 1 -> today.year - 1
    else -> null
}
