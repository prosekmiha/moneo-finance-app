package si.moneo.ui.goals

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmtDate
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.PillButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate
import java.time.YearMonth
import si.moneo.ui.components.centsToInput
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.parseCents
import si.moneo.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EMOJIS = listOf("🎯", "🚗", "🏠", "✈️", "🏖️", "💻", "📱", "🎓", "💍", "👶", "🐶", "🎸", "🚲", "🏥", "🎁", "💰")

/** Ustvarjanje ali urejanje cilja. [goal] == null pomeni nov cilj. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    goal: SavingsGoalEntity?,
    onDismiss: () -> Unit,
    onSave: (SavingsGoalEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var emoji by remember { mutableStateOf(goal?.emoji ?: EMOJIS.first()) }
    var target by remember { mutableStateOf(goal?.targetCents?.let(::centsToInput) ?: "") }
    var deadline by remember { mutableStateOf(goal?.deadline) }
    var pickDate by remember { mutableStateOf(false) }
    val wasAuto = (goal?.monthlyAutoCents ?: 0) > 0
    var autoOn by remember { mutableStateOf(wasAuto) }
    var autoAmount by remember { mutableStateOf(goal?.monthlyAutoCents?.let(::centsToInput) ?: "") }
    var autoDay by remember { mutableIntStateOf(goal?.autoDay ?: 1) }
    val autoCents = parseCents(autoAmount) ?: 0
    val targetCents = parseCents(target) ?: 0
    val valid = title.isNotBlank() && targetCents > 0 && (!autoOn || autoCents > 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(if (goal == null) R.string.new_goal else R.string.edit_goal), style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(EMOJIS) { e ->
                    val selected = e == emoji
                    Surface(
                        onClick = { emoji = e },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(52.dp).border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(e, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            OutlinedTextField(
                title, { title = it },
                label = { Text(stringResource(R.string.name_hint_holiday)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                target, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) target = v },
                label = { Text(stringResource(R.string.target_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(onClick = { pickDate = true }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(Spacing.md))
                    Text(
                        deadline?.let { stringResource(R.string.deadline_label, millisToLocalDate(it).fmtDate()) }
                            ?: stringResource(R.string.add_deadline),
                        modifier = Modifier.weight(1f),
                    )
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text(stringResource(R.string.remove)) }
                }
            }
            // Samodejno mesečno vplačilo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_saving_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.auto_saving_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoOn, onCheckedChange = { autoOn = it })
            }
            if (autoOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        autoAmount, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) autoAmount = v },
                        label = { Text(stringResource(R.string.per_month_eur)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(stringResource(R.string.day_of_month), (1..28).map { it to str(R.string.day_ordinal, it) }, autoDay, { autoDay = it }, Modifier.weight(1f))
                }
                val today = LocalDate.now()
                // Enako pravilo kot FinanceRepository.applyAutoSavings: ob vklopu se tekoči termin ne zapiše za nazaj
                val first = if (wasAuto && autoDay == goal?.autoDay && goal.lastAutoMonth != null) {
                    YearMonth.parse(goal.lastAutoMonth).plusMonths(1).atDay(autoDay).let { if (it.isBefore(today)) today else it }
                } else {
                    val thisMonth = today.withDayOfMonth(autoDay)
                    if (today.dayOfMonth >= autoDay) thisMonth.plusMonths(1) else thisMonth
                }
                Text(
                    stringResource(R.string.next_deposit, first.fmtDate()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillButton(
                stringResource(R.string.save), Icons.Rounded.Check,
                onClick = {
                    onSave(
                        (goal ?: SavingsGoalEntity(title = "", targetCents = 0)).copy(
                            title = title.trim(), emoji = emoji, targetCents = targetCents, deadline = deadline,
                            monthlyAutoCents = if (autoOn) autoCents else null,
                            autoDay = autoDay,
                            // Ob (ponovnem) vklopu ali spremembi dneva začni sveže - brez vplačil za nazaj
                            lastAutoMonth = if (autoOn && wasAuto && autoDay == goal?.autoDay) goal.lastAutoMonth else null,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                filled = true,
                enabled = valid,
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.delete_goal), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = deadline?.let { millisToLocalDate(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        deadline = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}
