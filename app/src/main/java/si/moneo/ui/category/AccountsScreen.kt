package si.moneo.ui.category

import si.moneo.ui.theme.underWhiteText
import si.moneo.ui.theme.Radius
import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import si.moneo.ui.components.dragAfterLongPress
import si.moneo.ui.components.dragHandle
import si.moneo.ui.components.rememberReorderState
import si.moneo.ui.components.reorderActions
import si.moneo.ui.components.reorderableItem
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.defaultAccount
import si.moneo.ui.MainViewModel
import si.moneo.ui.components.AnimatedAmount
import si.moneo.ui.components.PillButton
import si.moneo.ui.components.ScreenTopBar
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Spacing
import si.moneo.ui.theme.accentFor
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import si.moneo.ui.components.LocalSnackbar
import si.moneo.ui.str
import si.moneo.ui.theme.Finance

@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit, onTransfer: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val balances = home.accounts
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    // Račun v urejanju (null = urejevalnik zaprt)
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val defaultUid = balances.map { it.account }.defaultAccount()?.uid

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val showHint by vm.showAccountReorderHint.collectAsStateWithLifecycle()
    // Račun, ki ga brišemo, in število njegovih transakcij (odprto vprašanje, kam z njimi)
    var deleting by remember { mutableStateOf<Pair<AccountEntity, Int>?>(null) }

    fun delete(account: AccountEntity, moveTo: String?) {
        scope.launch {
            val removal = vm.deleteAccount(account, moveTo)
            val r = snackbar.showSnackbar(str(R.string.deleted_named, account.title), actionLabel = str(R.string.undo), duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.restoreAccount(removal)
        }
    }

    // Vrstni red računov velja povsod v aplikaciji (Domov, izbirniki, statistika)
    val reorder = rememberReorderState(
        listState,
        keys = balances.map { it.account.uid },
        onDragEnd = { if (vm.showAccountReorderHint.value) vm.dismissAccountReorderHint() },
        onCommit = vm::reorderAccounts,
    )
    val shown = reorder.arrange(balances) { it.account.uid }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.accounts), onBack)
        LazyColumn(
            Modifier.navigationBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Column(Modifier.padding(bottom = Spacing.sm)) {
                    Text(stringResource(R.string.total_balance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Različnih valut ne seštevamo: velika številka je v valuti privzetega računa, ostale so pod njo
                    val mainCurrency = balances.map { it.account }.defaultAccount()?.currencyCode ?: "EUR"
                    val totals = balances.groupBy { it.account.currencyCode }.mapValues { (_, l) -> l.sumOf { it.balanceCents } }
                    AnimatedAmount(totals[mainCurrency] ?: 0, style = MaterialTheme.typography.displaySmall, masked = hidden, currency = mainCurrency)
                    val others = totals.filterKeys { it != mainCurrency }
                    if (others.isNotEmpty()) {
                        Text(
                            others.entries.joinToString("  ·  ") { (currency, cents) -> if (hidden) "•••• $currency" else formatCents(cents, currency) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Prenos sredstev je mogoč šele, ko obstajata vsaj dva računa
            if (balances.size >= 2) {
                item {
                    PillButton(stringResource(R.string.new_transfer), Icons.Rounded.SwapHoriz, onTransfer, Modifier.fillMaxWidth())
                }
            }
            if (showHint && balances.size >= 2) {
                item(key = "reorder_hint") {
                    Surface(
                        onClick = vm::dismissAccountReorderHint,
                        shape = RoundedCornerShape(Radius.md),
                        color = Finance.colors.selectedTab,
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DragIndicator, null, tint = Finance.colors.onSelectedTab, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.accounts_reorder_hint), color = Finance.colors.onSelectedTab, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Close, stringResource(R.string.close_hint), tint = Finance.colors.onSelectedTab, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            items(shown, key = { b -> b.account.uid }) { b ->
                val uid = b.account.uid
                val dragged = uid == reorder.draggingKey
                val elevation by animateDpAsState(if (dragged) 12.dp else 0.dp, label = "dragElevation")
                val base = accentFor(b.account.title, b.account.color).underWhiteText()
                val reorderable = shown.size > 1
                val editLabel = stringResource(R.string.edit)
                Row(
                    Modifier
                        .reorderableItem(reorder, uid, this)
                        .shadow(elevation, RoundedCornerShape(Radius.lg))
                        .fillMaxWidth().height(110.dp).clip(RoundedCornerShape(Radius.lg))
                        .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.7f))))
                        .then(if (reorderable) Modifier.dragAfterLongPress(reorder, uid) else Modifier)
                        // Dolg pritisk brez premika ne odpre urejanja
                        .clickable(onClickLabel = editLabel) { if (reorder.draggingKey == null) editing = b.account }
                        .then(if (reorderable) Modifier.reorderActions(reorder, uid) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.account.title, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
                            if (uid == defaultUid) {
                                Text(
                                    stringResource(R.string.default_short),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = Spacing.sm).clip(RoundedCornerShape(Radius.sm))
                                        .background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(if (hidden) "•••• €" else formatCents(b.balanceCents, b.account.currencyCode), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.weight(1f))
                        val change = b.monthChangeCents
                        Text(
                            if (hidden) "•••• ${b.account.currencyCode}"
                            else stringResource(R.string.account_month_change, (if (change > 0) "+" else "") + formatCents(change, b.account.currencyCode)),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    // Ročaj: vlečenje takoj, brez dolgega pritiska. Vrstni red računov velja povsod v aplikaciji.
                    if (reorderable) {
                        Icon(
                            Icons.Rounded.DragIndicator, null, tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxHeight().padding(end = 6.dp)
                                .dragHandle(reorder, uid)
                                .padding(horizontal = 12.dp),
                        )
                    }
                }
            }
            item {
                PillButton(stringResource(R.string.new_account), Icons.Rounded.Add, { creating = true }, Modifier.fillMaxWidth().padding(top = Spacing.sm), filled = true)
            }
        }
    }

    val titles = balances.map { it.account.title }.toSet()
    if (creating) {
        AccountEditorSheet(
            account = null,
            existingTitles = titles,
            onDismiss = { creating = false },
            onSave = { new ->
                vm.saveAccount(new.copy(position = (balances.maxOfOrNull { it.account.position } ?: 0) + 1))
                creating = false
            },
        )
    }
    editing?.let { acc ->
        AccountEditorSheet(
            account = acc,
            existingTitles = titles,
            currentBalanceCents = balances.firstOrNull { it.account.uid == acc.uid }?.balanceCents,
            isDefault = acc.uid == defaultUid,
            onDismiss = { editing = null },
            onSave = {
                vm.saveAccount(it)
                editing = null
            },
            // Zadnjega računa ne dovolimo izbrisati - transakcije potrebujejo privzeti račun
            onDelete = if (balances.size > 1) ({
                editing = null
                scope.launch {
                    // Prazen račun izbrišemo takoj (z možnostjo razveljavitve), sicer vprašamo, kam s transakcijami
                    val usage = vm.accountUsage(acc)
                    if (usage == 0) delete(acc, null) else deleting = acc to usage
                }
            }) else null,
        )
    }
    deleting?.let { (acc, usage) ->
        DeleteAccountDialog(
            account = acc,
            usage = usage,
            others = balances.map { it.account }.filter { it.uid != acc.uid },
            preferredUid = defaultUid,
            onDismiss = { deleting = null },
            onConfirm = { moveTo ->
                deleting = null
                delete(acc, moveTo)
            },
        )
    }
}

/**
 * Kam s transakcijami računa, ki ga brišemo: prenos na drug račun v isti valuti (skupaj z začetnim
 * stanjem, da se skupno stanje ne spremeni) ali brisanje skupaj z računom. [onConfirm] dobi ciljni
 * račun ali null za brisanje.
 */
@Composable
private fun DeleteAccountDialog(
    account: AccountEntity,
    usage: Int,
    others: List<AccountEntity>,
    preferredUid: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val sameCurrency = others.filter { it.currencyCode == account.currencyCode }
    var choice by remember { mutableStateOf((sameCurrency.firstOrNull { it.uid == preferredUid } ?: sameCurrency.firstOrNull())?.uid) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account_title, account.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                Text(stringResource(R.string.delete_account_usage, usage))
                Spacer(Modifier.height(Spacing.sm))
                sameCurrency.forEach { target ->
                    DeleteChoice(stringResource(R.string.delete_account_move, target.title), choice == target.uid) { choice = target.uid }
                }
                DeleteChoice(stringResource(R.string.delete_account_drop), choice == null) { choice = null }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(if (choice != null) R.string.delete_account_move_hint else R.string.delete_account_drop_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sameCurrency.size < others.size) {
                    Text(
                        stringResource(R.string.delete_account_currency_note, account.currencyCode),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xs))
            .selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
