package si.moneo.ui.add

import si.moneo.ui.theme.Radius
import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.PillButton
import si.moneo.ui.formatCents
import si.moneo.ui.parseCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing

private class SplitPart(categoryUid: String?, amount: String = "") {
    var categoryUid by mutableStateOf(categoryUid)
    var amount by mutableStateOf(amount)
}

/**
 * Razdelitev enega računa na več kategorij (npr. 40 € hrana + 15 € gospodinjstvo).
 * Prvi del dobi ostanek do skupnega zneska; ob shranjevanju nastane en vnos na del.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSheet(
    totalCents: Long,
    categories: List<CategoryEntity>,
    initialCategoryUid: String?,
    onDismiss: () -> Unit,
    onSave: (List<Pair<String?, Long>>) -> Unit,
) {
    val colors = Finance.colors
    var firstCategory by remember { mutableStateOf(initialCategoryUid) }
    val others = remember { mutableStateListOf(SplitPart(null)) }
    val otherCents = others.map { parseCents(it.amount) ?: 0 }
    val remainder = totalCents - otherCents.sum()
    val valid = remainder > 0 && otherCents.all { it > 0 }
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.no_category)) + categories.map { it.uid to it.title }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.split_title, formatCents(totalCents)), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.split_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Prvi del: ostanek
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DropdownField(stringResource(R.string.category), options, firstCategory, { firstCategory = it }, Modifier.weight(1f))
                Column(Modifier.width(120.dp), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.remainder), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatCents(remainder),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (remainder > 0) MaterialTheme.colorScheme.onSurface else colors.expense,
                    )
                }
                Spacer(Modifier.width(40.dp))
            }

            others.forEachIndexed { index, part ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DropdownField(stringResource(R.string.category), options, part.categoryUid, { part.categoryUid = it }, Modifier.weight(1f))
                    OutlinedTextField(
                        part.amount, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) part.amount = v },
                        label = { Text("€") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.width(120.dp),
                    )
                    IconButton(onClick = { others.removeAt(index) }, enabled = others.size > 1, modifier = Modifier.width(40.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.remove_part))
                    }
                }
            }

            TextButton(onClick = { others += SplitPart(null) }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_part))
            }

            if (remainder <= 0) {
                Text(
                    stringResource(R.string.split_too_much, formatCents(totalCents), formatCents(1)),
                    style = MaterialTheme.typography.bodySmall, color = colors.expense,
                )
            }

            PillButton(
                stringResource(R.string.save_n, pluralStringResource(R.plurals.entries_acc, others.size + 1, others.size + 1)), Icons.AutoMirrored.Rounded.CallSplit,
                onClick = {
                    onSave(listOf(firstCategory to remainder) + others.map { it.categoryUid to (parseCents(it.amount) ?: 0) })
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                filled = true,
                enabled = valid,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
