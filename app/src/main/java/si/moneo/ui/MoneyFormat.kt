package si.moneo.ui

import androidx.annotation.StringRes
import si.moneo.R
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Znesek v obliki jezika aplikacije: "1.234,56 €" (sl, de), "€1,234.56" (en) ... */
fun formatCents(cents: Long, currency: String = "EUR"): String {
    val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { nf.currency = Currency.getInstance(currency) }
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return nf.format(BigDecimal.valueOf(cents, 2))
}

/**
 * Vzorec datuma iz prevodov (vsak jezik svoj zapis, npr. "d. M. yyyy" / "d MMM yyyy").
 * Brez naloženih virov (enotni testi) uporabi [fallback].
 */
fun datePattern(@StringRes id: Int, fallback: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (L10n.ready) str(id) else fallback, Locale.getDefault())

fun LocalDate.fmt(@StringRes id: Int, fallback: String): String = format(datePattern(id, fallback))

/** "30. 9. 2026" / "30 Sep 2026" */
fun LocalDate.fmtDate(): String = fmt(R.string.fmt_date, "d. M. yyyy")

/** "30. 9." / "30 Sep" */
fun LocalDate.fmtDayMonth(): String = fmt(R.string.fmt_day_month, "d. M.")

/** 1234567 -> "1.234.567" (slovensko ločilo tisočic). */
fun groupThousands(value: Long): String {
    val s = value.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

fun formatDate(millis: Long): String = millisToLocalDate(millis).fmtDate()

fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
