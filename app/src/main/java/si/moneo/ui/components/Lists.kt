package si.moneo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import si.moneo.ui.StatsPeriod
import si.moneo.ui.homePeriodLabel
import si.moneo.ui.theme.Spacing
import java.time.LocalDate

/** Ponoven tap na že izbran zavihek (pot zavihka); zaslon se ob tem pomakne na vrh. */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<String>> { MutableSharedFlow() }


@Composable
fun ScrollToTopOnReselect(route: String, state: LazyListState) {
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect, route, state) {
        reselect.filter { it == route }.collect { state.animateScrollToItem(0) }
    }
}

@Composable
fun ScrollToTopOnReselect(route: String, state: ScrollState) {
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect, route, state) {
        reselect.filter { it == route }.collect { state.animateScrollTo(0) }
    }
}

/** Nadomestna vrstica transakcije, dokler se podatki nalagajo. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(fill))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(CircleShape).background(fill))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(CircleShape).background(fill))
        }
        Spacer(Modifier.width(Spacing.md))
        Box(Modifier.width(56.dp).height(14.dp).clip(CircleShape).background(fill))
    }
}

/** Glava dneva v kronološkem seznamu: "Danes", "Včeraj", "sob, 3. 10. 2026" in opcijska vsota dneva. */
@Composable
fun DayHeader(
    date: LocalDate,
    modifier: Modifier = Modifier,
    amount: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    color: Color = MaterialTheme.colorScheme.surface,
    horizontalPadding: Dp = Spacing.screen,
) {
    Surface(color = color, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                homePeriodLabel(StatsPeriod.DAY, date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (amount != null) {
                Text(amount, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = amountColor)
            }
        }
    }
}
