@file:OptIn(ExperimentalSharedTransitionApi::class)

package si.moneo.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Obseg skupnih prehodov (nastavi ga MainActivity okoli NavHost). */
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Animacijski obseg trenutnega cilja navigacije. */
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Element, ki med prehodom "zleti" na isto mesto z enakim [key] na drugem zaslonu
 * (npr. ikona kategorije iz seznama v glavo podrobnosti). Brez obsega (npr. v predogledu) ne naredi nič.
 */
@Composable
fun Modifier.sharedElementKey(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    return with(shared) {
        this@sharedElementKey.sharedBounds(rememberSharedContentState(key), anim)
    }
}
