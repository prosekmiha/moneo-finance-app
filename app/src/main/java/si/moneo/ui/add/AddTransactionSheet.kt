package si.moneo.ui.add

import si.moneo.ui.theme.Radius
import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.qty
import androidx.compose.ui.res.stringResource
import si.moneo.ui.fmtDayMonth
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.defaultAccount
import si.moneo.ui.recurring.frequencyLabel
import si.moneo.feature.ocr.ReceiptScanActivity
import si.moneo.feature.voice.VoiceInputActivity
import si.moneo.ui.MainViewModel
import si.moneo.ui.category.AccountEditorSheet
import si.moneo.ui.category.CategoryEditorSheet
import si.moneo.ui.components.CategoryIcon
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.SwapHoriz
import si.moneo.ui.components.AttachmentThumbnail
import si.moneo.ui.components.AttachmentViewer
import si.moneo.ui.formatCents
import si.moneo.ui.components.LocalSnackbar
import si.moneo.ui.components.NumPad
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.SegmentedTabs
import si.moneo.ui.components.TagInput
import si.moneo.ui.joinTags
import si.moneo.ui.parseTags
import si.moneo.ui.components.centsToInput
import si.moneo.ui.components.expressionPreview
import si.moneo.ui.components.prettyExpression
import si.moneo.ui.evaluateAmountExpression
import si.moneo.ui.millisToLocalDate
import si.moneo.ui.theme.Finance
import si.moneo.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Vnos/urejanje transakcije kot bottom sheet z lastno numerično tipkovnico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    vm: MainViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        // List naj ne seže pod statusno vrstico/izrez kamere, kjer dotike prestreže sistem;
        // če vsebina ne gre na zaslon, zgornji del zdrsi (tipkovnica in Shrani ostaneta pritrjena)
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        // Nižji ročaj od privzetega (48dp), da gre celoten vnos na zaslon brez drsenja
        dragHandle = {
            Box(
                Modifier.padding(vertical = 10.dp).size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        },
    ) {
        AddTransactionContent(vm, onDone = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AddTransactionContent(vm: MainViewModel, onDone: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val isEdit by vm.draftIsEdit.collectAsStateWithLifecycle()
    val allCategories by vm.categories.collectAsStateWithLifecycle()
    val usage by vm.categoryUsage.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val transferMode by vm.transferMode.collectAsStateWithLifecycle()
    val transfer by vm.transferDraft.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val knownTags by vm.tags.collectAsStateWithLifecycle()
    var favoriteDialog by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = Finance.colors

    // Ključ seje: ob menjavi transakcija <-> prenos se znesek in opomba preneseta (glej switchMode)
    val sessionKey = if (transferMode) transfer.uid else draft.uid
    val initialCents = if (transferMode) transfer.fromAmountCents else draft.amountCents
    var expression by remember(sessionKey) { mutableStateOf(if (initialCents > 0) centsToInput(initialCents) else "") }
    var comment by remember(sessionKey) { mutableStateOf(if (transferMode) transfer.comment else draft.comment) }
    var viewAttachment by remember { mutableStateOf(false) }
    var repeat by remember(sessionKey) { mutableStateOf<RecurrenceFrequency?>(null) }
    var repeatAuto by remember(sessionKey) { mutableStateOf(true) }
    var suggestedUid by remember(draft.uid) { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<si.moneo.data.db.entity.CategoryEntity?>(null) }
    var newAccount by remember { mutableStateOf(false) }

    val isExpense = draft.type == TransactionType.EXPENSE
    val accent by animateColorAsState(
        when {
            transferMode -> colors.transfer
            isExpense -> colors.expense
            else -> colors.income
        },
        label = "accent",
    )
    val cents = evaluateAmountExpression(expression.replace('−', '-'))?.coerceAtLeast(0) ?: 0
    LaunchedEffect(cents, transferMode) {
        if (transferMode) vm.transferDraft.value = vm.transferDraft.value.copy(fromAmountCents = cents)
        else if (cents != vm.draft.value.amountCents) vm.draft.value = vm.draft.value.copy(amountCents = cents)
    }

    fun switchMode(mode: EntryMode) {
        when (mode) {
            EntryMode.TRANSFER -> if (!transferMode) {
                vm.startTransfer()
                vm.transferDraft.value = vm.transferDraft.value.copy(fromAmountCents = cents, comment = comment, date = draft.date)
            }
            else -> {
                val type = if (mode == EntryMode.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME
                if (transferMode) {
                    vm.startDraft(type)
                    vm.draft.value = vm.draft.value.copy(amountCents = cents, comment = comment, date = transfer.date)
                } else {
                    vm.draft.value = draft.copy(type = type, categoryUid = null)
                }
            }
        }
    }
    val mode = when {
        transferMode -> EntryMode.TRANSFER
        isExpense -> EntryMode.EXPENSE
        else -> EntryMode.INCOME
    }
    val dateMillis = if (transferMode) transfer.date else draft.date
    fun setDate(millis: Long) {
        if (transferMode) vm.transferDraft.value = vm.transferDraft.value.copy(date = millis)
        else vm.draft.value = vm.draft.value.copy(date = millis)
    }

    // Predlog kategorije iz opombe (z zamikom, da ne poizvedujemo ob vsaki črki)
    LaunchedEffect(comment, draft.type) {
        if (comment.isBlank()) { suggestedUid = null; return@LaunchedEffect }
        delay(350)
        suggestedUid = vm.suggestCategory(comment, draft.type)?.takeIf { uid -> allCategories.any { it.uid == uid && it.type == draft.type } }
    }

    val categories = remember(allCategories, usage, draft.type, suggestedUid) {
        allCategories.filter { it.type == draft.type }
            .sortedWith(compareByDescending<si.moneo.data.db.entity.CategoryEntity> { it.uid == suggestedUid }.thenByDescending { usage[it.uid] ?: 0 })
    }
    // Mreža kategorij: vidni sta dve vrsti, ostale so dosegljive z drsenjem navzdol
    val categoryGrid = rememberLazyGridState()
    // Ob vsaki preureditvi (prihod statistike uporabe, predlog, nova kategorija) mrežo postavi
    // na izbrano kategorijo oz. na vrh — sicer bi LazyGrid sledil prej vidnemu elementu po ključu.
    // Predlagana kategorija se uvrsti na začetek, zato takrat vedno na vrh.
    val categoryOrder = remember(categories) { categories.map { it.uid } }
    LaunchedEffect(draft.type, categoryOrder) {
        val i = categories.indexOfFirst { it.uid == draft.categoryUid }
        categoryGrid.scrollToItem(if (suggestedUid == null && i >= 0) i else 0)
    }
    val categoryTileHeight = 64.dp + with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }

    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
    Column(
        Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    transferMode && isEdit -> stringResource(R.string.edit_transfer)
                    transferMode -> stringResource(R.string.new_transfer)
                    isEdit -> stringResource(R.string.edit_transaction)
                    else -> stringResource(R.string.new_transaction)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!transferMode) {
                IconButton(onClick = { favoriteDialog = true }, enabled = cents > 0) {
                    Icon(Icons.Rounded.StarOutline, stringResource(R.string.save_as_favorite), tint = if (cents > 0) colors.warning else MaterialTheme.colorScheme.outline)
                }
            }
            if (isEdit) {
                IconButton(onClick = {
                    val wasTransfer = transferMode
                    val uid = if (wasTransfer) transfer.uid else draft.uid
                    if (wasTransfer) vm.deleteTransfer(uid) else vm.delete(uid)
                    onDone()
                    scope.launch {
                        val r = snackbar.showSnackbar(str(if (wasTransfer) R.string.transfer_deleted else R.string.transaction_deleted), actionLabel = str(R.string.undo), duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) {
                            if (wasTransfer) vm.restoreTransfer(uid) else vm.restore(uid)
                        }
                    }
                }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = colors.expense) }
            } else {
                IconButton(onClick = { context.startActivity(Intent(context, VoiceInputActivity::class.java)); onDone() }) {
                    Icon(Icons.Rounded.Mic, stringResource(R.string.voice_entry), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { context.startActivity(Intent(context, ReceiptScanActivity::class.java)); onDone() }) {
                    Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.scan_receipt), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        SegmentedTabs(
            // Pri urejanju obstoječega vnosa vrste (transakcija/prenos) ni mogoče zamenjati
            options = when {
                isEdit && transferMode -> listOf(EntryMode.TRANSFER)
                isEdit -> listOf(EntryMode.EXPENSE, EntryMode.INCOME)
                accounts.size < 2 -> listOf(EntryMode.EXPENSE, EntryMode.INCOME)
                else -> EntryMode.entries.toList()
            },
            selected = mode,
            onSelect = ::switchMode,
            label = { it.label },
            selectedColor = accent,
            onSelectedColor = Color.White,
        )

        // Velik znesek
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val pulse by animateFloatAsState(if (expression.isEmpty()) 1f else 1.02f, label = "pulse")
            Text(
                (if (expression.isEmpty()) "0" else prettyExpression(expression)) + " €",
                style = if (expression.length > 12) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                color = if (expression.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.scale(pulse),
            )
            AnimatedVisibility(expressionPreview(expression) != null) {
                Text(
                    expressionPreview(expression) ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Kategorije (prenos jih nima)
        if (!transferMode) LazyVerticalGrid(
            columns = GridCells.Adaptive(68.dp),
            state = categoryGrid,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxWidth().height(categoryTileHeight * 2 + Spacing.xs),
        ) {
            items(categories, key = { it.uid }) { cat ->
                val selected = draft.categoryUid == cat.uid
                val ring by animateColorAsState(if (selected) accent else Color.Transparent, label = "ring")
                Column(
                    Modifier.height(categoryTileHeight).clip(RoundedCornerShape(Radius.md))
                        .combinedClickable(
                            onLongClickLabel = stringResource(R.string.edit_category),
                            // Dolg pritisk: urejanje (preimenovanje, barva, ključne besede)
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); editingCategory = cat },
                            onClick = { vm.draft.value = draft.copy(categoryUid = if (selected) null else cat.uid) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        CategoryIcon(
                            cat.title, cat.color, type = cat.type, size = 46.dp,
                            modifier = Modifier.border(2.dp, ring, CircleShape).padding(3.dp),
                        )
                        if (cat.uid == suggestedUid && !selected) {
                            Icon(
                                Icons.Rounded.AutoAwesome, stringResource(R.string.suggestion),
                                tint = colors.warning,
                                modifier = Modifier.align(Alignment.TopEnd).size(16.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cat.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            item(key = "new-category") {
                Column(
                    Modifier.height(categoryTileHeight).clip(RoundedCornerShape(Radius.md))
                        .clickable { newCategory = true }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(52.dp).padding(3.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.new_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (transferMode) {
            TransferAccounts(
                accounts = accounts,
                fromUid = transfer.fromAccountUid,
                toUid = transfer.toAccountUid,
                onFrom = { vm.transferDraft.value = vm.transferDraft.value.copy(fromAccountUid = it) },
                onTo = { vm.transferDraft.value = vm.transferDraft.value.copy(toAccountUid = it) },
                onSwap = {
                    val t = vm.transferDraft.value
                    vm.transferDraft.value = t.copy(fromAccountUid = t.toAccountUid, toAccountUid = t.fromAccountUid)
                },
            )
        }

        // Račun + datum
        val date = millisToLocalDate(dateMillis)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            var accountMenu by remember { mutableStateOf(false) }
            if (!transferMode) Box {
                InfoChip(
                    Icons.Rounded.AccountBalanceWallet,
                    accounts.firstOrNull { it.uid == draft.accountUid }?.title ?: accounts.defaultAccount()?.title ?: stringResource(R.string.account),
                ) { accountMenu = true }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(text = { Text(acc.title) }, onClick = {
                            vm.draft.value = draft.copy(accountUid = acc.uid); accountMenu = false
                        })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_account), color = MaterialTheme.colorScheme.primary) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { accountMenu = false; newAccount = true },
                    )
                }
            }
            val today = LocalDate.now()
            DateChip(stringResource(R.string.today), date == today) { setDate(today.toMillis()) }
            DateChip(stringResource(R.string.yesterday), date == today.minusDays(1)) { setDate(today.minusDays(1).toMillis()) }
            val other = date != today && date != today.minusDays(1)
            InfoChip(
                Icons.Rounded.CalendarMonth,
                if (other) date.fmtDayMonth() else "",
                selected = other,
            ) { showDatePicker = true }
        }

        OutlinedTextField(
            value = comment,
            onValueChange = {
                comment = it
                if (transferMode) vm.transferDraft.value = vm.transferDraft.value.copy(comment = it)
                else vm.draft.value = vm.draft.value.copy(comment = it)
            },
            placeholder = { Text(stringResource(if (transferMode) R.string.note_transfer_hint else R.string.note_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Oznake + ponavljanje + razdelitev v eni vrstici (prihrani prostor, da ni treba drseti)
        if (!transferMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TagInput(
                    selected = parseTags(draft.tags),
                    known = knownTags.map { it.tag },
                    onChange = { vm.draft.value = vm.draft.value.copy(tags = joinTags(it)) },
                    modifier = Modifier.weight(1f),
                )
                if (!isEdit) RepeatChip(repeat) { repeat = it }
                // Razdelitev računa na več kategorij (en vnos na kategorijo)
                if (cents > 0) {
                    InfoChip(Icons.AutoMirrored.Rounded.CallSplit, "", contentDescription = stringResource(R.string.split_into_categories)) { showSplit = true }
                }
            }
            AnimatedVisibility(!isEdit && repeat != null) {
                RepeatAutoSwitch(repeatAuto) { repeatAuto = it }
            }
        }

        val attachment = draft.attachmentPath
        if (!transferMode && attachment != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttachmentThumbnail(attachment, Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm))) { viewAttachment = true }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.receipt_image), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.tap_to_view), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = vm::removeAttachment) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            }
        }

        Spacer(Modifier.height(Spacing.xs))
    }

    // Pritrjeno spodaj: vedno vidna tipkovnica in gumb Shrani
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.screen).padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        NumPad(expression, onChange = { expression = it }, keyHeight = 46.dp, gap = 6.dp)

        val transferValid = transfer.fromAccountUid != null && transfer.toAccountUid != null &&
            transfer.fromAccountUid != transfer.toAccountUid
        PillButton(
            text = stringResource(if (isEdit) R.string.save_changes else R.string.save),
            icon = Icons.Rounded.Check,
            onClick = {
                if (expressionPreview(expression) != null) expression = centsToInput(cents)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val message = if (transferMode) {
                    vm.saveTransfer()
                    str(if (isEdit) R.string.transfer_updated else R.string.transfer_saved)
                } else {
                    val roundUp = vm.saveDraft(repeat, repeatAuto)
                    val goal = goals.firstOrNull { it.goal.uid == vm.roundUpGoalUid.value }?.goal
                    when {
                        roundUp > 0 && goal != null -> str(R.string.saved_roundup, formatCents(roundUp), goal.emoji, goal.title)
                        repeat != null -> str(R.string.saved_repeats, frequencyLabel(repeat!!))
                        isEdit -> str(R.string.changes_saved)
                        else -> str(R.string.transaction_added)
                    }
                }
                onDone()
                scope.launch { snackbar.showSnackbar(message) }
            },
            enabled = cents > 0 && (!transferMode || transferValid),
            filled = true,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
        Spacer(Modifier.height(Spacing.xs))
    }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = millisToLocalDate(dateMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        setDate(picked.toMillis())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    if (showSplit) {
        SplitSheet(
            totalCents = cents,
            categories = categories,
            initialCategoryUid = draft.categoryUid,
            onDismiss = { showSplit = false },
            onSave = { parts ->
                if (expressionPreview(expression) != null) expression = centsToInput(cents)
                vm.saveSplit(parts)
                showSplit = false
                onDone()
                scope.launch { snackbar.showSnackbar(str(R.string.split_done, qty(R.plurals.entries_acc, parts.size, parts.size))) }
            },
        )
    }

    if (favoriteDialog) {
        var title by remember {
            mutableStateOf(draft.comment.trim().ifEmpty { allCategories.firstOrNull { it.uid == draft.categoryUid }?.title.orEmpty() })
        }
        AlertDialog(
            onDismissRequest = { favoriteDialog = false },
            title = { Text(stringResource(R.string.save_as_favorite)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.favorite_dialog_text, formatCents(cents)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.button_label)) }, singleLine = true, shape = RoundedCornerShape(Radius.md))
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    vm.saveDraftAsFavorite(title)
                    favoriteDialog = false
                    scope.launch { snackbar.showSnackbar(str(R.string.saved_to_favorites, title.trim())) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { favoriteDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (viewAttachment) {
        draft.attachmentPath?.let { AttachmentViewer(it) { viewAttachment = false } }
    }

    if (newCategory) {
        CategoryEditorSheet(
            category = null,
            initialType = draft.type,
            existingTitles = allCategories.filter { it.type == draft.type }.map { it.title }.toSet(),
            onDismiss = { newCategory = false },
            onSave = { cat ->
                vm.saveCategory(cat.copy(position = (allCategories.maxOfOrNull { it.position } ?: 0) + 1))
                // Nova kategorija je takoj izbrana (če ustreza tipu transakcije)
                if (cat.type == vm.draft.value.type) vm.draft.value = vm.draft.value.copy(categoryUid = cat.uid)
                newCategory = false
            },
        )
    }

    editingCategory?.let { cat ->
        CategoryEditorSheet(
            category = cat,
            initialType = cat.type,
            existingTitles = allCategories.filter { it.type == cat.type }.map { it.title }.toSet(),
            onDismiss = { editingCategory = null },
            onSave = {
                vm.saveCategory(it)
                editingCategory = null
            },
        )
    }

    if (newAccount) {
        AccountEditorSheet(
            account = null,
            existingTitles = accounts.map { it.title }.toSet(),
            onDismiss = { newAccount = false },
            onSave = { acc ->
                vm.saveAccount(acc.copy(position = (accounts.maxOfOrNull { it.position } ?: 0) + 1))
                vm.draft.value = vm.draft.value.copy(accountUid = acc.uid)
                newAccount = false
            },
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, selected: Boolean = false, contentDescription: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            if (text.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
            }
        }
    }
}

@Composable
private fun DateChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Finance.colors.selectedTab else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Finance.colors.onSelectedTab else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private fun LocalDate.toMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private enum class EntryMode(private val labelRes: Int) {
    EXPENSE(R.string.entry_expense), INCOME(R.string.entry_income), TRANSFER(R.string.transfer);

    val label: String get() = str(labelRes)
}

/** Izbira računov za prenos: "z računa" -> "na račun" z gumbom za zamenjavo. */
@Composable
private fun TransferAccounts(
    accounts: List<si.moneo.data.db.entity.AccountEntity>,
    fromUid: String?,
    toUid: String?,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onSwap: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccountPicker(stringResource(R.string.from_account), accounts, fromUid, onFrom, Modifier.weight(1f))
        IconButton(onClick = onSwap) { Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.swap), tint = Finance.colors.transfer) }
        AccountPicker(stringResource(R.string.to_account), accounts, toUid, onTo, Modifier.weight(1f))
    }
    if (fromUid != null && fromUid == toUid) {
        Text(stringResource(R.string.choose_two_accounts), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountPicker(
    label: String,
    accounts: List<si.moneo.data.db.entity.AccountEntity>,
    selectedUid: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.uid == selectedUid }
    Box(modifier) {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.title ?: stringResource(R.string.choose), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { acc ->
                DropdownMenuItem(text = { Text(acc.title) }, onClick = { onSelect(acc.uid); open = false })
            }
        }
    }
}

/** "Ponavljaj" kot kompaktna značka z menijem: brez / tedensko / mesečno / letno. */
@Composable
private fun RepeatChip(value: RecurrenceFrequency?, onChange: (RecurrenceFrequency?) -> Unit) {
    val options = listOf<RecurrenceFrequency?>(null, RecurrenceFrequency.WEEKLY, RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY)
    fun label(f: RecurrenceFrequency?) = f?.let { frequencyLabel(it).replaceFirstChar(Char::uppercase) } ?: str(R.string.repeat_no)
    var open by remember { mutableStateOf(false) }
    Box {
        InfoChip(
            Icons.Rounded.Autorenew,
            if (value == null) stringResource(R.string.repeat) else label(value),
            selected = value != null,
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { f ->
                DropdownMenuItem(
                    text = { Text(label(f), fontWeight = if (f == value) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = { if (f == value) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onChange(f); open = false },
                )
            }
        }
    }
}

/** Samodejni zapis ponavljajoče transakcije ali le opomnik. */
@Composable
private fun RepeatAutoSwitch(auto: Boolean, onAutoChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.record_automatically), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (auto) R.string.repeat_auto_on else R.string.repeat_auto_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = auto, onCheckedChange = onAutoChange)
    }
}
