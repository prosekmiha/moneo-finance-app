package si.moneo.ui.category

import si.moneo.ui.theme.underWhiteText
import si.moneo.ui.theme.Radius
import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
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

@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit, onTransfer: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val balances = home.accounts
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    // Račun v urejanju (null = urejevalnik zaprt)
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val defaultUid = balances.map { it.account }.defaultAccount()?.uid

    // Vrstni red med vlečenjem (uid-ji). Ostane tudi po spustu, dokler baza ne vrne enakega vrstnega reda,
    // sicer bi kartica za trenutek skočila nazaj.
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingUid by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val shown = dragOrder?.let { order ->
        val byUid = balances.associateBy { it.account.uid }
        order.mapNotNull(byUid::get) + balances.filter { it.account.uid !in order }
    } ?: balances
    LaunchedEffect(balances, draggingUid) {
        if (draggingUid == null && dragOrder == balances.map { it.account.uid }) dragOrder = null
    }
    // Kretnje živijo dlje od ene rekompozicije, zato berejo vedno svež seznam
    val currentShown by rememberUpdatedState(shown)
    val currentBalances by rememberUpdatedState(balances)

    fun startDrag(uid: String) {
        settleJob?.cancel()
        draggingUid = uid
        dragOffset = 0f
        dragOrder = currentShown.map { it.account.uid }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(dy: Float) {
        val uid = draggingUid ?: return
        val order = dragOrder ?: return
        dragOffset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == uid } ?: return
        // Ko sredina vlečene kartice preide na sosednjo, zamenjata mesti
        val center = (current.offset + dragOffset + current.size / 2f).toInt()
        val target = items.firstOrNull { it.key != uid && it.key in order && center in it.offset..(it.offset + it.size) } ?: return
        dragOrder = order.toMutableList().apply { add(order.indexOf(target.key), removeAt(order.indexOf(uid))) }
        // Osnovni položaj kartice se premakne na mesto soseda; zamik to izravna, da ostane pod prstom
        dragOffset += current.offset - target.offset
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun endDrag() {
        val order = dragOrder
        if (order == null || order == currentBalances.map { it.account.uid }) dragOrder = null
        else vm.reorderAccounts(order)
        // Kartica gladko pristane na svojem mestu
        settleJob = scope.launch {
            animate(dragOffset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragOffset = v }
            draggingUid = null
        }
    }

    fun moveBy(uid: String, step: Int) {
        val order = currentShown.map { it.account.uid }
        val i = order.indexOf(uid)
        if (i < 0 || i + step !in order.indices) return
        vm.reorderAccounts(order.toMutableList().apply { add(i + step, removeAt(i)) })
    }

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
                    AnimatedAmount(balances.sumOf { it.balanceCents }, style = MaterialTheme.typography.displaySmall, masked = hidden)
                }
            }
            // Prenos sredstev je mogoč šele, ko obstajata vsaj dva računa
            if (balances.size >= 2) {
                item {
                    PillButton(stringResource(R.string.new_transfer), Icons.Rounded.SwapHoriz, onTransfer, Modifier.fillMaxWidth())
                }
            }
            itemsIndexed(shown, key = { _, b -> b.account.uid }) { i, b ->
                val uid = b.account.uid
                val dragged = uid == draggingUid
                val elevation by animateDpAsState(if (dragged) 12.dp else 0.dp, label = "dragElevation")
                val base = accentFor(b.account.title, b.account.color).underWhiteText()
                val reorderable = shown.size > 1
                val moveUp = stringResource(R.string.move_up)
                val moveDown = stringResource(R.string.move_down)
                Row(
                    Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        // Vlečena kartica sledi prstu, ostale se animirano umaknejo
                        .animateItem(
                            placementSpec = if (dragged) null
                            else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                        )
                        .graphicsLayer {
                            translationY = if (dragged) dragOffset else 0f
                            val scale = if (dragged) 1.03f else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(elevation, RoundedCornerShape(Radius.lg))
                        .fillMaxWidth().height(110.dp).clip(RoundedCornerShape(Radius.lg))
                        .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.7f))))
                        .then(
                            if (reorderable) Modifier.pointerInput(uid) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startDrag(uid) },
                                    onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() },
                                )
                            } else Modifier,
                        )
                        // Dolg pritisk brez premika ne odpre urejanja
                        .clickable { if (draggingUid == null) editing = b.account }
                        .semantics {
                            // TalkBack ne more vleči, zato premik ponudimo kot dejanje
                            if (reorderable) customActions = listOfNotNull(
                                if (i > 0) CustomAccessibilityAction(moveUp) { moveBy(uid, -1); true } else null,
                                if (i < shown.lastIndex) CustomAccessibilityAction(moveDown) { moveBy(uid, 1); true } else null,
                            )
                        },
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
                        Row {
                            Text("•••• ${b.account.currencyCode}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.edit), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // Ročaj: vlečenje takoj, brez dolgega pritiska. Vrstni red računov velja povsod v aplikaciji.
                    if (reorderable) {
                        Icon(
                            Icons.Rounded.DragIndicator, null, tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxHeight().padding(end = 6.dp)
                                .pointerInput(uid) {
                                    detectDragGestures(
                                        onDragStart = { startDrag(uid) },
                                        onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                    )
                                }
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
                vm.deleteAccount(acc)
                editing = null
            }) else null,
        )
    }
}
