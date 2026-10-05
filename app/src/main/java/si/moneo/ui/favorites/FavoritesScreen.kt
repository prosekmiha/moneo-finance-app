package si.moneo.ui.favorites

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.CategoryIcon
import si.moneo.ui.components.DropdownField
import si.moneo.ui.components.EmptyState
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.SoftCard
import si.moneo.ui.components.centsToInput
import si.moneo.ui.formatCents
import si.moneo.ui.parseCents
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing

/** Kaj ureja urejevalnik priljubljenih: obstoječ vnos ali nov (null). */
class FavoriteEditTarget(val favorite: FavoriteEntity?)

/** Upravljanje priljubljenih vnosov: dodajanje, urejanje, vrstni red. */
@Composable
fun FavoritesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FavoriteEditTarget?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.favorites), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    stringResource(R.string.favorites_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (favorites.isEmpty()) {
                item { EmptyState(Icons.Rounded.Bolt, stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_hint)) }
            }
            itemsIndexed(favorites, key = { _, f -> f.uid }) { i, f ->
                val cat = categories.firstOrNull { it.uid == f.categoryUid }
                SoftCard(Modifier.fillMaxWidth(), onClick = { editing = FavoriteEditTarget(f) }, contentPadding = PaddingValues(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(cat?.title ?: f.title, cat?.color, type = f.type, size = 40.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(f.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(cat?.title, f.comment.ifBlank { null }).joinToString(" · ").ifEmpty { stringResource(R.string.no_category) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            (if (f.type == TransactionType.EXPENSE) "−" else "+") + formatCents(f.amountCents),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                            color = if (f.type == TransactionType.INCOME) Finance.colors.income else MaterialTheme.colorScheme.onSurface,
                        )
                        Column {
                            IconButton(onClick = { vm.moveFavorite(f, up = true) }, enabled = i > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { vm.moveFavorite(f, up = false) }, enabled = i < favorites.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                            }
                        }
                    }
                }
            }
            item {
                PillButton(stringResource(R.string.new_favorite), Icons.Rounded.Add, { editing = FavoriteEditTarget(null) }, Modifier.fillMaxWidth().padding(top = Spacing.sm), filled = true)
            }
        }
    }

    editing?.let { target ->
        FavoriteEditorSheet(
            favorite = target.favorite,
            categories = categories,
            accounts = accounts,
            onDismiss = { editing = null },
            onSave = { vm.saveFavorite(it); editing = null },
            onDelete = target.favorite?.let { f -> { vm.deleteFavorite(f); editing = null } },
        )
    }
}

/**
 * Vrstica hitrih gumbov na Domov: tap doda vnos, dolg pritisk ga uredi.
 * Zadnji gumb doda nov priljubljen vnos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesRow(
    favorites: List<FavoriteEntity>,
    categories: List<CategoryEntity>,
    onAdd: (FavoriteEntity) -> Unit,
    onEdit: (FavoriteEntity) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        itemsIndexed(favorites, key = { _, f -> f.uid }) { _, f ->
            val cat = categories.firstOrNull { it.uid == f.categoryUid }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier
                        .combinedClickable(
                            onClickLabel = stringResource(R.string.add_named, f.title),
                            onLongClickLabel = stringResource(R.string.edit),
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEdit(f) },
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAdd(f) },
                        )
                        .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(cat?.title ?: f.title, cat?.color, type = f.type, size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(f.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                        Text(
                            formatCents(f.amountCents),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (f.type == TransactionType.INCOME) Finance.colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "new-favorite") {
            Surface(onClick = onNew, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    if (favorites.isEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_favorite), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteEditorSheet(
    favorite: FavoriteEntity?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (FavoriteEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(favorite?.title ?: "") }
    var amount by remember { mutableStateOf(favorite?.amountCents?.let(::centsToInput) ?: "") }
    var type by remember { mutableStateOf(favorite?.type ?: TransactionType.EXPENSE) }
    var categoryUid by remember { mutableStateOf(favorite?.categoryUid) }
    var accountUid by remember { mutableStateOf(favorite?.accountUid) }
    var comment by remember { mutableStateOf(favorite?.comment ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = parseCents(amount) ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(if (favorite == null) R.string.new_favorite else R.string.edit_favorite), style = MaterialTheme.typography.titleLarge)
            SegmentedTabs(
                listOf(TransactionType.EXPENSE, TransactionType.INCOME), type,
                {
                    type = it
                    if (categories.firstOrNull { c -> c.uid == categoryUid }?.type != it) categoryUid = null
                },
                label = { str(if (it == TransactionType.EXPENSE) R.string.entry_expense else R.string.entry_income) },
            )
            OutlinedTextField(
                title, { title = it }, label = { Text(stringResource(R.string.button_label_hint)) },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                amount, { v -> if (v.all { it.isDigit() || it == ',' || it == '.' }) amount = v },
                label = { Text(stringResource(R.string.amount_eur)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DropdownField(
                    stringResource(R.string.category),
                    listOf<Pair<String?, String>>(null to stringResource(R.string.none)) + categories.filter { it.type == type }.map { it.uid to it.title },
                    categoryUid, { categoryUid = it }, Modifier.weight(1f),
                )
                DropdownField(
                    stringResource(R.string.account),
                    listOf<Pair<String?, String>>(null to stringResource(R.string.default_short)) + accounts.map { it.uid to it.title },
                    accountUid, { accountUid = it }, Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                comment, { comment = it }, label = { Text(stringResource(R.string.entry_note_optional)) },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            PillButton(
                stringResource(R.string.save), Icons.Rounded.Check,
                onClick = {
                    onSave(
                        (favorite ?: FavoriteEntity(title = "", amountCents = 0)).copy(
                            title = title.trim(), type = type, amountCents = cents,
                            categoryUid = categoryUid, accountUid = accountUid, comment = comment.trim(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                filled = true,
                enabled = cents > 0 && title.isNotBlank(),
            )
            if (onDelete != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(stringResource(if (confirmDelete) R.string.tap_again_to_confirm else R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
