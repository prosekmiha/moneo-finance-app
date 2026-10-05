package si.moneo.ui.components

import si.moneo.ui.theme.Radius
import si.moneo.R
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmtDate
import si.moneo.ui.fmtDayMonth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Polja v obrazcih (urejevalniki naročnin, dolgov ...): izbira s seznama in datum. */

@Composable
fun <T> DropdownField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldSurface(label, options.firstOrNull { it.first == selected }?.second ?: options.first().second) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

/** Polje za datum; [clearable] doda možnost "Brez" (null). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldSurface(label, value?.let(::shortDate) ?: stringResource(R.string.none)) { open = true }
        if (clearable && value != null) {
            IconButton(onClick = { onChange(null) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
fun FieldSurface(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "12. 10." v tekočem letu, sicer "12. 10. 2025" (oblika jezika aplikacije). */
fun shortDate(d: LocalDate): String = if (d.year == LocalDate.now().year) d.fmtDayMonth() else d.fmtDate()
