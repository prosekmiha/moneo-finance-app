package si.moneo.ui.add

import si.moneo.ui.theme.Radius
import si.moneo.R
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.TransactionUi
import si.moneo.ui.components.AttachmentThumbnail
import si.moneo.ui.components.AttachmentViewer
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.signedAmount
import si.moneo.ui.components.sourceLabel
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pregled transakcije pred urejanjem: vse podrobnosti, slika računa in hitra dejanja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    tx: TransactionUi,
    ruleTitle: String?,
    subscriptionTitle: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = Finance.colors
    var viewImage by remember { mutableStateOf(false) }
    // Odpri v celoti - sicer gumba Izbriši/Uredi ostaneta pod robom zaslona
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CategoryIcon(tx.categoryTitle ?: tx.comment.ifBlank { null }, tx.categoryColor, type = tx.type, size = 72.dp)
            Spacer(Modifier.height(Spacing.md))
            Text(tx.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                signedAmount(tx),
                style = MaterialTheme.typography.displayMedium,
                color = if (tx.type == TransactionType.INCOME) colors.income else MaterialTheme.colorScheme.onSurface,
            )
            if (!tx.confirmed) {
                Text(
                    stringResource(R.string.unconfirmed_check),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.warning,
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            Column(Modifier.fillMaxWidth()) {
                DetailRow(Icons.Rounded.CalendarMonth, stringResource(R.string.date), tx.date.fmt(R.string.fmt_day_full, "EEEE, d. MMMM yyyy").replaceFirstChar { it.uppercase() })
                DetailRow(Icons.Rounded.Category, stringResource(R.string.category), tx.categoryTitle ?: stringResource(R.string.no_category))
                DetailRow(Icons.Rounded.AccountBalanceWallet, stringResource(R.string.account), tx.accountTitle ?: stringResource(if (tx.accountUid == null) R.string.default_account else R.string.deleted_account))
                if (tx.comment.isNotBlank()) DetailRow(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.note), tx.comment)
                if (tx.tags.isNotEmpty()) DetailRow(Icons.AutoMirrored.Rounded.Label, stringResource(R.string.tags), tx.tags.joinToString(", ") { "#$it" })
                DetailRow(Icons.Rounded.Source, stringResource(R.string.source), sourceLabel(tx.source)?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.manual_entry))
                if (ruleTitle != null) DetailRow(Icons.Rounded.Autorenew, stringResource(R.string.recurrence), stringResource(R.string.rule_named, ruleTitle))
                if (subscriptionTitle != null) DetailRow(Icons.Rounded.Autorenew, stringResource(R.string.subscription), subscriptionTitle)
            }

            tx.attachmentPath?.let { path ->
                Spacer(Modifier.height(Spacing.md))
                AttachmentThumbnail(path, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(Radius.lg))) { viewImage = true }
                Text(stringResource(R.string.tap_to_enlarge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Spacing.xl))
            if (!tx.confirmed) {
                PillButton(stringResource(R.string.confirm), Icons.Rounded.Check, onConfirm, Modifier.fillMaxWidth(), filled = true, tint = colors.income)
                Spacer(Modifier.height(Spacing.sm))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PillButton(stringResource(R.string.delete), Icons.Rounded.DeleteOutline, onDelete, Modifier.weight(1f), tint = colors.expense)
                PillButton(stringResource(R.string.edit), Icons.Rounded.Edit, onEdit, Modifier.weight(1f), filled = tx.confirmed)
            }
            Spacer(Modifier.height(Spacing.sm))
            // Nov vnos z enakim zneskom, kategorijo, računom in opombo - z današnjim datumom
            PillButton(stringResource(R.string.repeat_entry_today), Icons.Rounded.Replay, onRepeat, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.xl))
        }
    }
    if (viewImage) tx.attachmentPath?.let { AttachmentViewer(it) { viewImage = false } }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
