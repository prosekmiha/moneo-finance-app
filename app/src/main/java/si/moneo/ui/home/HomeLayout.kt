package si.moneo.ui.home

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.moneo.ui.theme.Spacing

/** Kartice na Domov med stanjem in seznamom kategorij, ki jih uporabnik lahko skrije/premakne. */
enum class HomeSection(private val labelRes: Int, private val descriptionRes: Int) {
    BUDGETS(R.string.section_budgets, R.string.section_budgets_desc),
    FAVORITES(R.string.favorites, R.string.section_favorites_desc),
    UPCOMING(R.string.section_upcoming, R.string.section_upcoming_desc),
    YEAR_REVIEW(R.string.section_year_review, R.string.section_year_review_desc),
    GOALS(R.string.savings_goals, R.string.section_goals_desc),
    ;

    val label: String get() = str(labelRes)
    val description: String get() = str(descriptionRes)
}

data class HomeSectionState(val section: HomeSection, val visible: Boolean)

/** Privzeta postavitev: vse vidno, v tem vrstnem redu. */
val DEFAULT_HOME_LAYOUT = HomeSection.entries.map { HomeSectionState(it, true) }

/**
 * Prebere postavitev iz nastavitev ("FAVORITES,-UPCOMING,..." - minus = skrito).
 * Neznane vrednosti se prezrejo, nove kartice (iz novejše različice) se dodajo na konec kot vidne.
 */
fun parseHomeLayout(raw: String?): List<HomeSectionState> {
    if (raw.isNullOrBlank()) return DEFAULT_HOME_LAYOUT
    val parsed = raw.split(',').mapNotNull { token ->
        val hidden = token.startsWith("-")
        HomeSection.entries.firstOrNull { it.name == token.removePrefix("-") }?.let { HomeSectionState(it, !hidden) }
    }.distinctBy { it.section }
    return parsed + HomeSection.entries.filter { s -> parsed.none { it.section == s } }.map { HomeSectionState(it, true) }
}

fun serializeHomeLayout(layout: List<HomeSectionState>): String =
    layout.joinToString(",") { (if (it.visible) "" else "-") + it.section.name }

/** Urejanje domače strani: vklop/izklop kartic in vrstni red. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutSheet(
    layout: List<HomeSectionState>,
    onChange: (List<HomeSectionState>) -> Unit,
    onDismiss: () -> Unit,
) {
    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in layout.indices) return
        onChange(layout.toMutableList().apply { add(j, removeAt(i)) })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding()) {
            Text(stringResource(R.string.home_screen), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.home_layout_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = Spacing.md),
            )
            layout.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { move(i, 1) }, enabled = i < layout.lastIndex, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                        }
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(item.section.label, style = MaterialTheme.typography.bodyLarge)
                        Text(item.section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = item.visible,
                        onCheckedChange = { v -> onChange(layout.map { if (it.section == item.section) it.copy(visible = v) else it }) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onChange(DEFAULT_HOME_LAYOUT) }) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
