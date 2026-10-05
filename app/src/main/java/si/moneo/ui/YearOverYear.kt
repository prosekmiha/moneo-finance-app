package si.moneo.ui

import si.moneo.R
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Isto obdobje lani za [bucket] kot [začetek, konec). Pri tekočem (še nedokončanem) obdobju
 * se primerja le do istega dne lani, da primerjava ni nepoštena (pol meseca proti celemu).
 */
fun lastYearRange(bucket: PeriodBucket, today: LocalDate): Pair<LocalDate, LocalDate> {
    val endExclusive = if (today.isBefore(bucket.endExclusive)) today.plusDays(1) else bucket.endExclusive
    return bucket.start.minusYears(1) to endExclusive.minusYears(1)
}

private fun List<TransactionUi>.inRange(from: LocalDate, toExclusive: LocalDate) =
    filter { it.confirmed && !it.date.isBefore(from) && it.date.isBefore(toExclusive) }

/** Lanski prihodki in stroški v istem obdobju. */
data class LastYearTotals(val incomeCents: Long, val expenseCents: Long, val hasData: Boolean)

fun lastYearTotals(txs: List<TransactionUi>, bucket: PeriodBucket, today: LocalDate): LastYearTotals {
    val (from, to) = lastYearRange(bucket, today)
    val ly = txs.inRange(from, to)
    return LastYearTotals(
        incomeCents = ly.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
        expenseCents = ly.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
        hasData = ly.isNotEmpty(),
    )
}

/** Ključ kategorije za primerjavo (tip + uid; brez kategorije = [UNCATEGORIZED]). */
fun categoryKey(type: TransactionType, categoryUid: String?): String = "${type.name}:${categoryUid ?: UNCATEGORIZED.uid}"

/** Lanske vsote po kategorijah v istem obdobju. */
fun lastYearByCategory(txs: List<TransactionUi>, bucket: PeriodBucket, today: LocalDate): Map<String, Long> {
    val (from, to) = lastYearRange(bucket, today)
    return txs.inRange(from, to).groupBy { categoryKey(it.type, it.categoryUid) }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
}

/**
 * Nasveti o kategorijah, ki so se glede na isto obdobje lani opazno spremenile
 * (vsaj 20 % in vsaj 10 €), največja razlika najprej.
 */
fun yearOverYearInsights(
    txs: List<TransactionUi>,
    categories: List<CategoryEntity>,
    bucket: PeriodBucket,
    today: LocalDate,
    max: Int = 2,
): List<Insight> {
    val lastYear = lastYearByCategory(txs, bucket, today)
    val now = txs.filter { it.confirmed && it.type == TransactionType.EXPENSE && it.date in bucket && !it.date.isAfter(today) }
        .groupBy { it.categoryUid }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
    val byUid = categories.associateBy { it.uid }
    return now.mapNotNull { (uid, cur) ->
        val cat = uid?.let { byUid[it] } ?: return@mapNotNull null
        val prev = lastYear[categoryKey(TransactionType.EXPENSE, uid)] ?: return@mapNotNull null
        val pct = percentDelta(cur, prev) ?: return@mapNotNull null
        if (abs(pct) < 20 || abs(cur - prev) < 1000) return@mapNotNull null
        Triple(cat, cur - prev, pct)
    }.sortedByDescending { abs(it.second) }.take(max).map { (cat, diff, pct) ->
        if (diff > 0) Insight(InsightKind.TREND_UP, str(R.string.yoy_more, cat.title, pct, formatCents(diff)))
        else Insight(InsightKind.TREND_DOWN, str(R.string.yoy_less, cat.title, -pct, formatCents(diff)))
    }
}

// ---------- Podrobnosti kategorije ----------

data class MonthComparison(val month: YearMonth, val thisYearCents: Long, val lastYearCents: Long) {
    val label: String get() = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.uppercase() }
}

data class CategoryYearComparison(
    val ytdCents: Long,
    val ytdLastYearCents: Long,
    val months: List<MonthComparison>,
) {
    val deltaPct: Int? get() = percentDelta(ytdCents, ytdLastYearCents)
    val hasLastYear: Boolean get() = ytdLastYearCents > 0
}

/** Kategorija letos (od 1. januarja do danes) proti istemu obdobju lani + po mesecih. */
fun categoryYearComparison(own: List<TransactionUi>, today: LocalDate): CategoryYearComparison {
    val confirmed = own.filter { it.confirmed }
    val startThis = today.withDayOfYear(1)
    val startLast = startThis.minusYears(1)
    val sameDayLast = today.minusYears(1)
    val ytd = confirmed.filter { !it.date.isBefore(startThis) && !it.date.isAfter(today) }.sumOf { it.amountCents }
    val ytdLast = confirmed.filter { !it.date.isBefore(startLast) && !it.date.isAfter(sameDayLast) }.sumOf { it.amountCents }
    val months = (1..today.monthValue).map { m ->
        val ym = YearMonth.of(today.year, m)
        MonthComparison(
            ym,
            confirmed.filter { YearMonth.from(it.date) == ym }.sumOf { it.amountCents },
            confirmed.filter { YearMonth.from(it.date) == ym.minusYears(1) }.sumOf { it.amountCents },
        )
    }.reversed()
    return CategoryYearComparison(ytd, ytdLast, months)
}
