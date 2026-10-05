package si.moneo.ui.components

import si.moneo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.moneo.ui.evaluateAmountExpression
import si.moneo.ui.formatCents

/** Numerična tipkovnica s preprostim kalkulatorjem (+ / −). */
@Composable
fun NumPad(
    expression: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 52.dp,
    gap: Dp = 8.dp,
) {
    val haptic = LocalHapticFeedback.current
    fun press(key: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChange(applyKey(expression, key))
    }
    val rows = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("7", "8", "9", "−"),
        listOf(",", "0", "00", "="),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    val isOp = key in setOf("⌫", "+", "−", "=")
                    Surface(
                        onClick = { press(key) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isOp) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f).height(keyHeight),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "⌫") {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.delete_key), modifier = Modifier.size(22.dp))
                            } else {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isOp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val OPS = setOf('+', '−')

/** Uporabi pritisnjeno tipko na izraz; skrbi za veljavnost (max 2 decimalki, brez dvojnih operatorjev ...). */
fun applyKey(expr: String, key: String): String {
    val last = expr.lastOrNull()
    val currentOperand = expr.takeLastWhile { it !in OPS }
    return when (key) {
        "⌫" -> expr.dropLast(1)
        "=" -> evaluateAmountExpression(expr.replace('−', '-'))?.takeIf { it >= 0 }?.let { centsToInput(it) } ?: expr
        "+", "−" -> when {
            expr.isEmpty() -> expr
            last in OPS -> expr.dropLast(1) + key
            last == ',' -> expr.dropLast(1) + key
            else -> expr + key
        }
        "," -> when {
            currentOperand.contains(',') -> expr
            currentOperand.isEmpty() -> "${expr}0,"
            else -> "$expr,"
        }
        else -> {
            val decimals = currentOperand.substringAfter(',', "")
            when {
                currentOperand.contains(',') && decimals.length + key.length > 2 -> expr
                currentOperand == "0" -> expr.dropLast(1) + key.trimStart('0').ifEmpty { "0" }
                currentOperand.isEmpty() && key == "00" -> expr + "0"
                currentOperand.filter { it.isDigit() }.length >= 9 -> expr
                else -> expr + key
            }
        }
    }
}

fun centsToInput(cents: Long): String =
    if (cents % 100 == 0L) (cents / 100).toString() else "%d,%02d".format(cents / 100, cents % 100)

/** Lepši prikaz izraza: "1234,5+20" -> "1.234,5 + 20". */
fun prettyExpression(expr: String): String =
    expr.split('+', '−').let { parts ->
        var i = 0
        val sb = StringBuilder()
        parts.forEachIndexed { idx, part ->
            val intPart = part.substringBefore(',')
            val dec = if (part.contains(',')) "," + part.substringAfter(',') else ""
            sb.append((intPart.toLongOrNull()?.let { si.moneo.ui.groupThousands(it) } ?: intPart) + dec)
            i += part.length
            if (idx < parts.lastIndex) {
                sb.append(" ${expr[i]} ")
                i++
            }
        }
        sb.toString()
    }

fun expressionPreview(expr: String): String? =
    if (expr.any { it in OPS } && expr.last() !in OPS) {
        evaluateAmountExpression(expr.replace('−', '-'))?.let { "= " + formatCents(it) }
    } else null
