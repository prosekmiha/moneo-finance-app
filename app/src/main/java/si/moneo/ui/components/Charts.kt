package si.moneo.ui.components

import si.moneo.R
import si.moneo.ui.str
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import si.moneo.ui.PeriodBucket
import si.moneo.ui.formatCents
import si.moneo.ui.theme.Finance
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stolpični graf prihodki/stroški po obdobjih. Izbrano obdobje je obarvano,
 * ostala siva; tap ali vlečenje izbere obdobje.
 */
@Composable
fun IncomeExpenseBarChart(
    buckets: List<PeriodBucket>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val colors = Finance.colors
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedLabelColor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(buckets.map { it.start }) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val n = buckets.size.coerceAtLeast(1)

    val a11y = remember(buckets, selectedIndex) {
        buckets.joinToString(". ") { str(R.string.chart_a11y_bucket, it.longLabel, formatCents(it.incomeCents), formatCents(it.expenseCents)) } +
            (buckets.getOrNull(selectedIndex)?.let { ". " + str(R.string.chart_a11y_selected, it.longLabel) } ?: "")
    }
    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = str(R.string.chart_a11y, a11y) }
            .pointerInput(n) {
                detectTapGestures { pos -> currentOnSelect((pos.x / (size.width / n)).toInt().coerceIn(0, n - 1)) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    currentOnSelect((change.position.x / (size.width / n)).toInt().coerceIn(0, n - 1))
                }
            },
    ) {
        val labelH = 22.dp.toPx()
        val chartH = size.height - labelH
        val maxV = buckets.maxOfOrNull { max(it.incomeCents, it.expenseCents) }?.coerceAtLeast(1) ?: 1
        val slot = size.width / n
        val barW = min(slot * 0.22f, 14.dp.toPx())
        val gap = 4.dp.toPx()
        val radius = CornerRadius(barW / 2, barW / 2)

        buckets.forEachIndexed { i, b ->
            val selected = i == selectedIndex
            val cx = slot * i + slot / 2
            val minH = barW
            fun bar(value: Long, left: Float, color: Color) {
                val h = max(minH, chartH * 0.92f * value / maxV) * grow.value
                drawRoundRect(color, Offset(left, chartH - h), Size(barW, h), radius)
            }
            bar(b.incomeCents, cx - gap / 2 - barW, if (selected) colors.income else colors.chartIdle)
            bar(b.expenseCents, cx + gap / 2, if (selected) colors.expense else colors.chartIdle.copy(alpha = 0.7f))

            val layout = measurer.measure(
                b.label,
                TextStyle(
                    fontSize = 11.sp,
                    color = if (selected) selectedLabelColor else labelColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, chartH + (labelH - layout.size.height) / 2f + 2f))
        }
    }
}

data class DonutSlice(val value: Float, val color: Color)

/** Kolobarni graf z animiranimi loki in izbiro segmenta s tapom. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    description: String = "",
    center: @Composable BoxScope.() -> Unit = {},
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(slices.size, slices.sumOf { it.value.toDouble() }) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(size).pointerInput(slices) {
                detectTapGestures { pos ->
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val d = pos - c
                    val dist = sqrt(d.x * d.x + d.y * d.y)
                    val outer = this.size.width / 2f
                    if (dist < outer - thickness.toPx() * 1.6f || dist > outer) {
                        currentOnSelect(null); return@detectTapGestures
                    }
                    var angle = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat() + 90f
                    if (angle < 0) angle += 360f
                    var acc = 0f
                    slices.forEachIndexed { i, s ->
                        acc += s.value / total * 360f
                        if (angle <= acc) {
                            currentOnSelect(if (currentSelected == i) null else i); return@detectTapGestures
                        }
                    }
                }
            },
        ) {
            val strokePx = thickness.toPx()
            val inset = strokePx * 0.8f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            var start = -90f
            val gapDeg = if (slices.size > 1) 1.5f else 0f
            slices.forEachIndexed { i, s ->
                val full = s.value / total * 360f
                val sw = (full * sweep.value - gapDeg).coerceAtLeast(0.1f)
                val isSel = selected == i
                val dim = selected != null && !isSel
                drawArc(
                    color = if (dim) s.color.copy(alpha = 0.3f) else s.color,
                    startAngle = start + gapDeg / 2,
                    sweepAngle = sw,
                    useCenter = false,
                    topLeft = if (isSel) Offset(inset - 4.dp.toPx(), inset - 4.dp.toPx()) else topLeft,
                    size = if (isSel) Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()) else arcSize,
                    style = Stroke(if (isSel) strokePx * 1.35f else strokePx, cap = StrokeCap.Butt),
                )
                start += full * sweep.value
            }
        }
        center()
    }
}

/** Krožni napredek (cilji, proračuni). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    thickness: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated = rememberGrowFrom0(progress.coerceIn(0f, 1f), durationMs = 900)
    Box(
        modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val s = thickness.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(s))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Mini trend linija z gradientnim polnilom. */
@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) { progress.snapTo(0f); progress.animateTo(1f, tween(900)) }
    Canvas(modifier.semantics { contentDescription = str(R.string.sparkline_a11y, values.joinToString { formatCents(it) }) }) {
        if (values.size < 2) return@Canvas
        val minV = values.min().toFloat()
        val maxV = values.max().toFloat()
        val range = (maxV - minV).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - (v - minV) / range * size.height * 0.85f - size.height * 0.075f)
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        clipRect(right = size.width * progress.value) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, 3.5.dp.toPx(), pts.last())
        }
    }
}
