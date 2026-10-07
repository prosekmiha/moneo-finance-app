package si.moneo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import si.moneo.ui.formatCents

/**
 * Znesek, ki se ob spremembi "prešteje" do nove vrednosti.
 * Interpolira med long vrednostma (brez izgube natančnosti float-a pri velikih zneskih).
 */
@Composable
fun AnimatedAmount(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    prefix: String = "",
    durationMs: Int = 800,
    /** true = namesto zneska prikaži "•••• €" (skrito stanje). */
    masked: Boolean = false,
    currency: String = "EUR",
) {
    if (masked) {
        Text("•••• €", modifier = modifier, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
        return
    }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(0L) }
    val fraction = remember { Animatable(1f) }
    LaunchedEffect(cents) {
        from = from + ((to - from) * fraction.value).toLong()
        to = cents
        fraction.snapTo(0f)
        fraction.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val shown = if (fraction.value >= 1f) to else from + ((to - from) * fraction.value).toLong()
    Text(prefix + formatCents(shown, currency), modifier = modifier, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
}
