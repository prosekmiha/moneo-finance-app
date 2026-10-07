package si.moneo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.moneo.R

/**
 * Razvrščanje elementov [LazyColumn]-a z vlečenjem (ključi so nizi).
 *
 * Vrstni red med vlečenjem ostane tudi po spustu, dokler vir (baza, nastavitve) ne vrne enakega
 * vrstnega reda, sicer bi element za trenutek skočil nazaj.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
) {
    /** Vrstni red med vlečenjem (null = enak viru). */
    internal var order by mutableStateOf<List<String>?>(null)
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    // Posodobljeno ob vsaki rekompoziciji; kretnje živijo dlje od nje, zato berejo vedno svež vir
    internal var keys: List<String> = emptyList()
    internal var onCommit: (List<String>) -> Unit = {}
    internal var onDragEnd: () -> Unit = {}
    private var settleJob: Job? = null

    /** Ključi v prikazanem vrstnem redu (z vlečenjem). */
    val shownKeys: List<String>
        get() = order?.let { o -> o.filter { it in keys } + keys.filter { it !in o } } ?: keys

    /** Elementi v prikazanem vrstnem redu. */
    fun <T> arrange(items: List<T>, key: (T) -> String): List<T> {
        val o = order ?: return items
        val byKey = items.associateBy(key)
        return o.mapNotNull(byKey::get) + items.filter { key(it) !in o }
    }

    fun start(key: String) {
        settleJob?.cancel()
        order = shownKeys
        draggingKey = key
        offset = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(dy: Float) {
        val key = draggingKey ?: return
        val o = order ?: return
        offset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        // Ko sredina vlečenega elementa preide na sosednjega, zamenjata mesti
        val center = (current.offset + offset + current.size / 2f).toInt()
        val target = items.firstOrNull { it.key != key && it.key in o && center in it.offset..(it.offset + it.size) } ?: return
        order = o.toMutableList().apply { add(o.indexOf(target.key), removeAt(o.indexOf(key))) }
        // Osnovni položaj se premakne na mesto soseda; zamik to izravna, da ostane pod prstom
        offset += current.offset - target.offset
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun end() {
        val o = order
        if (o == null || o == keys) order = null else onCommit(o)
        onDragEnd()
        // Element gladko pristane na svojem mestu
        settleJob = scope.launch {
            animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
            draggingKey = null
        }
    }

    /** Premik za [step] mest brez vlečenja (TalkBack). */
    fun move(key: String, step: Int) {
        val shown = shownKeys
        val i = shown.indexOf(key)
        if (i < 0 || i + step !in shown.indices) return
        onCommit(shown.toMutableList().apply { add(i + step, removeAt(i)) })
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    keys: List<String>,
    onDragEnd: () -> Unit = {},
    onCommit: (List<String>) -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val state = remember(listState) { ReorderState(listState, scope, haptic) }
    state.keys = keys
    state.onCommit = onCommit
    state.onDragEnd = onDragEnd
    LaunchedEffect(keys, state.draggingKey) {
        if (state.draggingKey == null && state.order == keys) state.order = null
    }
    return state
}

/** Vlečen element sledi prstu in je dvignjen; ostali se animirano umaknejo. */
fun Modifier.reorderableItem(state: ReorderState, key: String, itemScope: LazyItemScope): Modifier {
    val dragged = key == state.draggingKey
    return with(itemScope) {
        this@reorderableItem
            .zIndex(if (dragged) 1f else 0f)
            .animateItem(
                placementSpec = if (dragged) null
                else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
            )
            .graphicsLayer {
                translationY = if (dragged) state.offset else 0f
                val scale = if (dragged) 1.03f else 1f
                scaleX = scale
                scaleY = scale
            }
    }
}

/** Vlečenje po dolgem pritisku kjerkoli na elementu. */
fun Modifier.dragAfterLongPress(state: ReorderState, key: String): Modifier = pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = { state.start(key) },
        onDrag = { change, amount -> change.consume(); state.dragBy(amount.y) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
    )
}

/** Ročaj: vlečenje takoj, brez dolgega pritiska. */
fun Modifier.dragHandle(state: ReorderState, key: String): Modifier = pointerInput(key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDrag = { change, amount -> change.consume(); state.dragBy(amount.y) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
    )
}

/** TalkBack ne more vleči, zato premik ponudimo kot dejanji "Premakni gor/dol". */
@Composable
fun Modifier.reorderActions(state: ReorderState, key: String): Modifier {
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val shown = state.shownKeys
    val i = shown.indexOf(key)
    return semantics {
        customActions = listOfNotNull(
            if (i > 0) CustomAccessibilityAction(moveUp) { state.move(key, -1); true } else null,
            if (i in 0 until shown.lastIndex) CustomAccessibilityAction(moveDown) { state.move(key, 1); true } else null,
        )
    }
}
