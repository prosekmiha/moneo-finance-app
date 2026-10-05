package si.moneo.ui.debts

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.DebtEntity
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.DateField
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.SlimProgress
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

private class DebtEditTarget(val debt: DebtEntity?)

@Composable
fun DebtsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val debts by vm.debts.collectAsStateWithLifecycle()
    var showSettled by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DebtEditTarget?>(null) }
    val colors = Finance.colors

    val open = debts.filter { !it.settled }
    val owedToMe = open.filter { it.direction == DebtDirection.LENT }.sumOf { it.remainingCents }
    val iOwe = open.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.remainingCents }
    val shown = debts.filter { it.settled == showSettled }
        .sortedWith(compareBy<DebtEntity> { it.dueDate ?: Long.MAX_VALUE }.thenByDescending { it.date })

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.debts), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SoftCard(Modifier.weight(1f)) {
                        Text(stringResource(R.string.owed_to_me), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCents(owedToMe), style = MaterialTheme.typography.titleLarge, color = colors.income)
                    }
                    SoftCard(Modifier.weight(1f)) {
                        Text(stringResource(R.string.i_owe), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCents(iOwe), style = MaterialTheme.typography.titleLarge, color = colors.expense)
                    }
                }
            }
            item {
                SegmentedTabs(listOf(false, true), showSettled, { showSettled = it }, label = { if (it) str(R.string.settled_tab) else str(R.string.open_tab, open.size) })
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Handshake,
                        stringResource(if (showSettled) R.string.no_settled_debts else R.string.no_open_debts),
                        stringResource(if (showSettled) R.string.settled_hint else R.string.open_debts_hint),
                    )
                }
            }
            items(shown, key = { it.uid }) { debt -> DebtRow(debt) { editing = DebtEditTarget(debt) } }
            item {
                PillButton(stringResource(R.string.new_debt), Icons.Rounded.Add, { editing = DebtEditTarget(null) }, Modifier.fillMaxWidth().padding(top = Spacing.sm), filled = true)
            }
            item {
                Text(
                    stringResource(R.string.debts_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { target ->
        DebtEditorSheet(
            debt = target.debt,
            onDismiss = { editing = null },
            onSave = { vm.saveDebt(it); editing = null },
            onDelete = target.debt?.let { d -> { vm.deleteDebt(d); editing = null } },
        )
    }
}

@Composable
private fun DebtRow(debt: DebtEntity, onClick: () -> Unit) {
    val colors = Finance.colors
    val lent = debt.direction == DebtDirection.LENT
    val tint = if (lent) colors.income else colors.expense
    val due = debt.dueDate?.let(::millisToLocalDate)
    val overdue = due != null && !debt.settled && due.isBefore(LocalDate.now())
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(debt.person, size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(debt.person, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildList {
                        add(str(if (lent) R.string.owes_me else R.string.i_owe_short))
                        when {
                            debt.settled -> add(str(R.string.settled))
                            due != null -> add(str(R.string.due_on, shortDate(due), relativeDayLabel(due)))
                        }
                        debt.note.ifBlank { null }?.let { add(it) }
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCents(if (debt.settled) debt.amountCents else debt.remainingCents),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    color = if (debt.settled) MaterialTheme.colorScheme.onSurfaceVariant else tint,
                )
                if (debt.repaidCents > 0 && !debt.settled) {
                    Text(stringResource(R.string.of_amount_short, formatCents(debt.amountCents)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (debt.repaidCents > 0 && !debt.settled) {
            Spacer(Modifier.height(Spacing.sm))
            SlimProgress(debt.repaidCents.toFloat() / debt.amountCents, tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtEditorSheet(
    debt: DebtEntity?,
    onDismiss: () -> Unit,
    onSave: (DebtEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = ZoneId.systemDefault()
    fun LocalDate.millis() = atStartOfDay(zone).toInstant().toEpochMilli()
    var direction by remember { mutableStateOf(debt?.direction ?: DebtDirection.LENT) }
    var person by remember { mutableStateOf(debt?.person ?: "") }
    var amount by remember { mutableStateOf(debt?.amountCents?.let(::centsToInput) ?: "") }
    var repaid by remember { mutableStateOf(debt?.repaidCents?.takeIf { it > 0 }?.let(::centsToInput) ?: "") }
    var date by remember { mutableStateOf(debt?.date?.let(::millisToLocalDate) ?: LocalDate.now()) }
    var dueDate by remember { mutableStateOf(debt?.dueDate?.let(::millisToLocalDate)) }
    var note by remember { mutableStateOf(debt?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = parseCents(amount) ?: 0
    val repaidCents = (parseCents(repaid) ?: 0).coerceIn(0, cents.coerceAtLeast(0))

    fun build() = (debt ?: DebtEntity(person = "", direction = direction, amountCents = 0, date = 0)).copy(
        person = person.trim(), direction = direction, amountCents = cents, repaidCents = repaidCents,
        date = date.millis(), dueDate = dueDate?.millis(), note = note.trim(),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(if (debt == null) R.string.new_debt else R.string.edit_debt), style = MaterialTheme.typography.titleLarge)
            SegmentedTabs(
                DebtDirection.entries.toList(), direction, { direction = it },
                label = { str(if (it == DebtDirection.LENT) R.string.i_lent else R.string.i_borrowed) },
                selectedColor = if (direction == DebtDirection.LENT) Finance.colors.income else Finance.colors.expense,
                onSelectedColor = androidx.compose.ui.graphics.Color.White,
            )
            OutlinedTextField(
                person, { person = it }, label = { Text(stringResource(if (direction == DebtDirection.LENT) R.string.to_whom else R.string.from_whom)) },
                singleLine = true, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    amount, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) amount = v },
                    label = { Text(stringResource(R.string.amount_eur)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(Radius.md), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    repaid, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) repaid = v },
                    label = { Text(stringResource(R.string.already_repaid)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(Radius.md), modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DateField(stringResource(R.string.date), date, { if (it != null) date = it }, Modifier.weight(1f))
                DateField(stringResource(R.string.due_date), dueDate, { dueDate = it }, Modifier.weight(1f), clearable = true)
            }
            OutlinedTextField(
                note, { note = it }, label = { Text(stringResource(R.string.note_debt_hint)) },
                singleLine = true, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth(),
            )
            if (dueDate != null) {
                Text(
                    stringResource(R.string.debt_reminder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillButton(
                stringResource(R.string.save), Icons.Rounded.Check,
                onClick = { onSave(build()) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                filled = true,
                enabled = cents > 0 && person.isNotBlank(),
            )
            if (debt != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (!debt.settled) {
                        TextButton(onClick = { onSave(build().copy(repaidCents = cents)) }, enabled = cents > 0) {
                            Text(stringResource(R.string.all_repaid))
                        }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                            Text(stringResource(if (confirmDelete) R.string.tap_again_to_confirm else R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Box(Modifier.height(Spacing.sm))
        }
    }
}
