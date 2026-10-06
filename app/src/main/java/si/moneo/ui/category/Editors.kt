package si.moneo.ui.category

import androidx.compose.ui.graphics.luminance
import si.moneo.ui.theme.asGraphic
import si.moneo.ui.theme.underWhiteText
import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.centsToInput
import si.moneo.ui.parseCents
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.theme.AccentPalette
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor

/**
 * Nova ali obstoječa kategorija. [category] == null pomeni novo kategorijo tipa [initialType].
 * [onDelete] == null skrije gumb za brisanje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorSheet(
    category: CategoryEntity?,
    initialType: TransactionType,
    existingTitles: Set<String>,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(category?.title ?: "") }
    var type by remember { mutableStateOf(category?.type ?: initialType) }
    var color by remember { mutableStateOf(category?.color) }
    // Naziv je vedno ključna beseda (dodan ob shranjevanju), zato ga tu ne prikazujemo
    var keywords by remember {
        mutableStateOf(
            category?.keywords.orEmpty().split(',').map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(category?.title, ignoreCase = true) }
                .joinToString(", "),
        )
    }
    val trimmed = title.trim()
    val duplicate = trimmed.lowercase() in existingTitles.map { it.lowercase() } &&
        !trimmed.equals(category?.title, ignoreCase = true)

    EditorSheet(
        heading = stringResource(if (category == null) R.string.new_category else R.string.edit_category),
        onDismiss = onDismiss,
        saveEnabled = trimmed.isNotEmpty() && !duplicate,
        onSave = {
            val words = (listOf(trimmed) + keywords.split(','))
                .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            onSave(
                (category ?: CategoryEntity(title = trimmed, type = type)).copy(
                    title = trimmed,
                    type = type,
                    color = color,
                    keywords = words.joinToString(","),
                ),
            )
        },
        deleteLabel = stringResource(R.string.delete_category),
        onDelete = onDelete,
    ) {
        CategoryIcon(
            trimmed.ifEmpty { null }, color, type = type, size = 72.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (category == null) {
            SegmentedTabs(
                listOf(TransactionType.EXPENSE, TransactionType.INCOME), type, { type = it },
                label = { str(if (it == TransactionType.EXPENSE) R.string.entry_expense else R.string.entry_income) },
            )
        }
        OutlinedTextField(
            title, { title = it },
            label = { Text(stringResource(R.string.name)) },
            singleLine = true,
            isError = duplicate,
            supportingText = if (duplicate) ({ Text(stringResource(R.string.category_exists)) }) else null,
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        )
        ColorPicker(color?.let { Color(it) }, onPick = { color = it.toArgb() })
        OutlinedTextField(
            keywords, { keywords = it },
            label = { Text(stringResource(R.string.keywords_optional)) },
            supportingText = { Text(stringResource(R.string.keywords_hint)) },
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Nov ali obstoječ račun (npr. gotovina, kartica, varčevalni račun). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorSheet(
    account: AccountEntity?,
    existingTitles: Set<String>,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Trenutno stanje obstoječega računa; ob podanem stanju ga je mogoče ročno popraviti. */
    currentBalanceCents: Long? = null,
    /** Račun je trenutno privzeti; privzetost se ne izklopi, ampak prenese z izbiro drugega računa. */
    isDefault: Boolean = false,
) {
    var title by remember { mutableStateOf(account?.title ?: "") }
    var makeDefault by remember { mutableStateOf(isDefault) }
    var color by remember { mutableStateOf(account?.color) }
    var currency by remember { mutableStateOf(account?.currencyCode ?: "EUR") }
    fun signedInput(cents: Long) = (if (cents < 0) "-" else "") + centsToInput(kotlin.math.abs(cents))
    var initial by remember {
        mutableStateOf(account?.initialBalanceCents?.takeIf { it != 0L }?.let(::signedInput) ?: "")
    }
    val initialCents = parseCents(initial.replace("−", "-"))
    // Stanje = začetno stanje + vsota transakcij in prenosov. Popravek stanja zato prilagodi
    // začetno stanje, transakcije pa ostanejo nespremenjene; polji sta med seboj povezani.
    val movementsCents = if (account != null && currentBalanceCents != null) currentBalanceCents - account.initialBalanceCents else null
    var balance by remember { mutableStateOf(currentBalanceCents?.let(::signedInput) ?: "") }
    val balanceCents = parseCents(balance.replace("−", "-"))
    val trimmed = title.trim()
    val duplicate = trimmed.lowercase() in existingTitles.map { it.lowercase() } &&
        !trimmed.equals(account?.title, ignoreCase = true)

    EditorSheet(
        heading = stringResource(if (account == null) R.string.new_account else R.string.edit_account),
        onDismiss = onDismiss,
        saveEnabled = trimmed.isNotEmpty() && !duplicate && initialCents != null && (movementsCents == null || balanceCents != null),
        onSave = {
            onSave(
                (account ?: AccountEntity(title = trimmed)).copy(
                    title = trimmed, color = color, currencyCode = currency, initialBalanceCents = initialCents ?: 0,
                    isDefault = makeDefault,
                ),
            )
        },
        deleteLabel = stringResource(R.string.delete_account),
        onDelete = onDelete,
    ) {
        // Predogled kartice
        val base = accentFor(trimmed.ifEmpty { "Račun" }, color).underWhiteText()
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(Radius.lg))
                .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.7f)))),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(trimmed.ifEmpty { stringResource(R.string.new_account) }, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("•••• $currency", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            }
        }
        OutlinedTextField(
            title, { title = it },
            label = { Text(stringResource(R.string.account_name_hint)) },
            singleLine = true,
            isError = duplicate,
            supportingText = if (duplicate) ({ Text(stringResource(R.string.account_exists)) }) else null,
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        )
        if (movementsCents != null) {
            OutlinedTextField(
                balance,
                { v ->
                    if (v.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) {
                        balance = v
                        parseCents(v)?.let { initial = signedInput(it - movementsCents) }
                    }
                },
                label = { Text(stringResource(R.string.account_current_balance, currency)) },
                supportingText = { Text(stringResource(R.string.account_balance_correction_hint)) },
                isError = balanceCents == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            initial,
            { v ->
                if (v.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) {
                    initial = v
                    if (movementsCents != null) parseCents(v)?.let { balance = signedInput(it + movementsCents) }
                }
            },
            label = { Text(stringResource(R.string.opening_balance, currency)) },
            supportingText = { Text(stringResource(R.string.opening_balance_hint)) },
            isError = initialCents == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        )
        SegmentedTabs(listOf("EUR", "USD", "CHF", "GBP"), currency, { currency = it }, label = { it })
        ColorPicker(color?.let { Color(it) }, onPick = { color = it.toArgb() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.default_account), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.default_account_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = makeDefault, onCheckedChange = { makeDefault = it }, enabled = !isDefault, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    heading: String,
    onDismiss: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    deleteLabel: String,
    onDelete: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(heading, style = MaterialTheme.typography.titleLarge)
            content()
            PillButton(stringResource(R.string.save), Icons.Rounded.Check, onSave, Modifier.fillMaxWidth().height(56.dp), filled = true, enabled = saveEnabled)
            if (onDelete != null) {
                // Dvostopenjsko brisanje namesto ločenega dialoga
                TextButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (confirmDelete) stringResource(R.string.tap_again_to_confirm) else deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
fun ColorPicker(selected: Color?, onPick: (Color) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(AccentPalette) { c ->
            val isSel = selected == c
            // Shrani se izvirna barva; prikaže se različica z zadostnim kontrastom do podlage
            val shown = c.asGraphic()
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(shown)
                    .border(3.dp, if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, null, tint = if (shown.luminance() > 0.4f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

