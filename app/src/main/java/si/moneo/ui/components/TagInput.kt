package si.moneo.ui.components

import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import si.moneo.ui.theme.accentFor

/**
 * Izbira oznak pri vnosu: izbrane oznake (tap × odstrani), predlogi obstoječih oznak
 * in polje za novo oznako.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInput(
    selected: List<String>,
    known: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(selected.isNotEmpty()) }

    fun add(tag: String) {
        val t = tag.trim().trimEnd(',')
        if (t.isNotEmpty() && selected.none { it.equals(t, ignoreCase = true) }) onChange(selected + t)
        text = ""
    }

    val suggestions = known.filter { k -> selected.none { it.equals(k, ignoreCase = true) } }
        .filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }
        .take(6)

    Column(modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            selected.forEach { tag ->
                val c = accentFor(tag)
                Surface(shape = CircleShape, color = c.copy(alpha = 0.15f)) {
                    Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#$tag", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { onChange(selected - tag) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.tag_remove, tag), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            if (!expanded) {
                Surface(onClick = { expanded = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.Label, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (selected.isEmpty()) R.string.tag_add_chip else R.string.tag_add_another), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (expanded) {
            if (suggestions.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    suggestions.forEach { s ->
                        Surface(onClick = { add(s) }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("+ $s", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { v -> if (v.endsWith(",")) add(v) else text = v },
                placeholder = { Text(stringResource(R.string.tag_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(text) }),
                trailingIcon = {
                    if (text.isNotBlank()) IconButton(onClick = { add(text) }) { Icon(Icons.Rounded.Add, stringResource(R.string.tag_add)) }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
