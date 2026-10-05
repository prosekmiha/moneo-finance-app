package si.moneo.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Element se ob pritisku rahlo pomanjša in z vzmetjo vrne - otipljiv odziv kartic in gumbov. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Zamaknjen vstop elementov seznama ob odprtju zaslona. Animira le elemente, ki se pokažejo
 * v prvih [WINDOW_MS] - pri drsenju nazaj gor se ne ponavlja.
 */
class Stagger internal constructor(private val start: Long) {
    internal var next = 0

    /** Kliči na začetku vsebine LazyColumn, da indeksi ob ponovni sestavi začnejo pri 0. */
    fun reset() { next = 0 }

    internal val active: Boolean get() = SystemClock.uptimeMillis() - start < WINDOW_MS

    private companion object { const val WINDOW_MS = 900L }
}

@Composable
fun rememberStagger(): Stagger = remember { Stagger(SystemClock.uptimeMillis()) }

@Composable
fun Modifier.enterStagger(stagger: Stagger, index: Int): Modifier {
    val animate = remember { stagger.active }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(index.coerceAtMost(8) * 40L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}

/** Vrednost za napredek (progress), ki ob prvem prikazu "zraste" od 0, nato gladko sledi cilju. */
@Composable
fun rememberGrowFrom0(target: Float, durationMs: Int = 800): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return value.value
}

/** `item { }` z zamaknjenim vstopom; indeks se dodeli samodejno po vrstnem redu. */
fun LazyListScope.staggerItem(
    stagger: Stagger,
    key: Any? = null,
    contentType: Any? = null,
    content: @Composable LazyItemScope.() -> Unit,
) {
    val index = stagger.next++
    item(key, contentType) {
        Box(Modifier.enterStagger(stagger, index)) { content(this@item) }
    }
}
