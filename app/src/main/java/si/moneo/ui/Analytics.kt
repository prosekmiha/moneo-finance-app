package si.moneo.ui

import si.moneo.R
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.defaultAccount
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Čiste funkcije za izračune na zaslonih (brez Android odvisnosti) - lažje testiranje,
 * ViewModel jih le poveže s Flow-i.
 */

enum class StatsPeriod(private val labelRes: Int, private val previousRes: Int) {
    DAY(R.string.period_day, R.string.period_prev_day),
    WEEK(R.string.period_week, R.string.period_prev_week),
    MONTH(R.string.period_month, R.string.period_prev_month),
    QUARTER(R.string.period_quarter, R.string.period_prev_quarter),
    YEAR(R.string.period_year, R.string.period_prev_year);

    val label: String get() = str(labelRes)

    fun startOf(d: LocalDate): LocalDate = when (this) {
        DAY -> d
        WEEK -> d.with(DayOfWeek.MONDAY)
        MONTH -> d.withDayOfMonth(1)
        QUARTER -> d.withDayOfMonth(1).withMonth((d.monthValue - 1) / 3 * 3 + 1)
        YEAR -> d.withDayOfYear(1)
    }

    fun plus(start: LocalDate, n: Long): LocalDate = when (this) {
        DAY -> start.plusDays(n)
        WEEK -> start.plusWeeks(n)
        MONTH -> start.plusMonths(n)
        QUARTER -> start.plusMonths(3 * n)
        YEAR -> start.plusYears(n)
    }

    fun shortLabel(start: LocalDate): String = when (this) {
        DAY -> start.fmt(R.string.fmt_weekday_short, "EEE").take(3)
        WEEK -> start.fmtDayMonth()
        MONTH -> start.fmt(R.string.fmt_month_short, "LLL").trimEnd('.').take(3)
        QUARTER -> "Q${(start.monthValue - 1) / 3 + 1} '${start.year % 100}"
        YEAR -> start.year.toString()
    }

    fun longLabel(start: LocalDate): String = when (this) {
        DAY -> start.fmt(R.string.fmt_day_full, "EEEE, d. MMMM yyyy").replaceFirstChar { it.uppercase() }
        WEEK -> weekRange(start)
        MONTH -> start.fmt(R.string.fmt_month_year, "LLLL yyyy").replaceFirstChar { it.uppercase() }
        QUARTER -> str(R.string.quarter_long, (start.monthValue - 1) / 3 + 1, start.year)
        YEAR -> start.year.toString()
    }

    /** Kako imenujemo "prejšnje obdobje" v besedilu. */
    val previousName: String get() = str(previousRes)
}

/** "6. 10. – 12. 10. 2026" */
private fun weekRange(start: LocalDate): String {
    val end = start.plusDays(6)
    return str(R.string.date_range, start.fmtDayMonth(), end.fmtDate())
}

data class PeriodBucket(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val label: String,
    val longLabel: String,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
    operator fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(endExclusive)
}

data class CategorySpend(
    val category: CategoryEntity,
    val totalCents: Long,
    val share: Float,
    val count: Int,
)

enum class InsightKind { TOP_CATEGORY, TREND_UP, TREND_DOWN, BIG_EXPENSE, FORECAST, SAVINGS, BUDGET }

data class Insight(val kind: InsightKind, val text: String)

data class AccountBalance(val account: AccountEntity, val balanceCents: Long)

data class BudgetStatus(val category: CategoryEntity, val spentCents: Long, val budgetCents: Long) {
    val fraction: Float get() = if (budgetCents <= 0) 0f else spentCents.toFloat() / budgetCents
}

data class GoalUi(
    val goal: SavingsGoalEntity,
    val savedCents: Long,
    val contributions: List<GoalContributionEntity>,
    /** Predviden datum dosega cilja glede na povprečje zadnjih 3 mesecev (null = ni podatka / že dosežen). */
    val projectedDate: LocalDate?,
) {
    val progress: Float get() = if (goal.targetCents <= 0) 0f else (savedCents.toFloat() / goal.targetCents).coerceIn(0f, 1f)
    val reached: Boolean get() = savedCents >= goal.targetCents
}

private var uncategorizedCache: CategoryEntity? = null

/** Psevdo kategorija za vnose brez kategorije (naziv v jeziku aplikacije; isti primerek, dokler se jezik ne spremeni). */
val UNCATEGORIZED: CategoryEntity
    get() {
        val title = str(R.string.no_category)
        return uncategorizedCache?.takeIf { it.title == title }
            ?: CategoryEntity(uid = "__none__", title = title, type = TransactionType.EXPENSE).also { uncategorizedCache = it }
    }

fun buildBuckets(txs: List<TransactionUi>, period: StatsPeriod, count: Int, today: LocalDate): List<PeriodBucket> {
    val current = period.startOf(today)
    val starts = (count - 1 downTo 0).map { period.plus(current, -it.toLong()) }
    val first = starts.first()
    val income = LongArray(count)
    val expense = LongArray(count)
    txs.forEach { tx ->
        if (!tx.confirmed || tx.date.isBefore(first)) return@forEach
        val start = period.startOf(tx.date)
        val idx = starts.indexOf(start)
        if (idx < 0) return@forEach
        if (tx.type == TransactionType.INCOME) income[idx] += tx.amountCents else expense[idx] += tx.amountCents
    }
    return starts.mapIndexed { i, s ->
        PeriodBucket(s, period.plus(s, 1), period.shortLabel(s), period.longLabel(s), income[i], expense[i])
    }
}

fun categorySpends(
    txs: List<TransactionUi>,
    categories: List<CategoryEntity>,
    type: TransactionType = TransactionType.EXPENSE,
): List<CategorySpend> {
    val expenses = txs.filter { it.type == type && it.confirmed }
    val total = expenses.sumOf { it.amountCents }.coerceAtLeast(1)
    val byUid = categories.associateBy { it.uid }
    // Transakcije izbrisane kategorije štejejo pod "Brez kategorije" (ena sama vrstica)
    return expenses.groupBy { it.categoryUid?.takeIf { uid -> uid in byUid } }
        .map { (uid, list) ->
            val sum = list.sumOf { it.amountCents }
            CategorySpend(uid?.let { byUid[it] } ?: UNCATEGORIZED, sum, sum.toFloat() / total, list.size)
        }
        .sortedByDescending { it.totalCents }
}

fun percentDelta(current: Long, previous: Long): Int? =
    if (previous <= 0) null else (((current - previous).toDouble() / previous) * 100).roundToInt()

fun buildInsights(
    period: StatsPeriod,
    bucket: PeriodBucket,
    previous: PeriodBucket?,
    spends: List<CategorySpend>,
    txsInBucket: List<TransactionUi>,
    today: LocalDate,
): List<Insight> {
    val out = mutableListOf<Insight>()
    spends.firstOrNull()?.takeIf { it.category !== UNCATEGORIZED && it.share >= 0.15f }?.let {
        out += Insight(InsightKind.TOP_CATEGORY, str(R.string.insight_top_category, it.category.title, (it.share * 100).roundToInt()))
    }
    if (previous != null) {
        percentDelta(bucket.expenseCents, previous.expenseCents)?.let { d ->
            if (d >= 10) out += Insight(InsightKind.TREND_UP, str(R.string.insight_trend_up, d, period.previousName))
            else if (d <= -10) out += Insight(InsightKind.TREND_DOWN, str(R.string.insight_trend_down, abs(d), period.previousName))
        }
    }
    // Napoved do konca tekočega obdobja
    if (today in bucket && period != StatsPeriod.YEAR && bucket.expenseCents > 0) {
        val elapsed = ChronoUnit.DAYS.between(bucket.start, today) + 1
        val total = ChronoUnit.DAYS.between(bucket.start, bucket.endExclusive)
        if (elapsed in 3 until total) {
            val daily = bucket.expenseCents / elapsed
            out += Insight(
                InsightKind.FORECAST,
                str(R.string.insight_forecast, formatCents(daily), formatCents(daily * total)),
            )
        }
    }
    txsInBucket.filter { it.type == TransactionType.EXPENSE && it.confirmed }
        .maxByOrNull { it.amountCents }
        ?.takeIf { bucket.expenseCents > 0 && it.amountCents * 4 >= bucket.expenseCents && txsInBucket.size > 3 }
        ?.let {
            out += Insight(
                InsightKind.BIG_EXPENSE,
                str(
                    R.string.insight_big_expense,
                    it.categoryTitle ?: it.comment.ifBlank { str(R.string.no_description) },
                    formatCents(it.amountCents),
                    it.date.fmtDayMonth(),
                ),
            )
        }
    savingsRate(bucket)?.takeIf { it >= 20 }?.let {
        out += Insight(InsightKind.SAVINGS, str(R.string.insight_savings, it))
    }
    return out
}

fun savingsRate(bucket: PeriodBucket): Int? =
    if (bucket.incomeCents <= 0) null
    else ((bucket.netCents.toDouble() / bucket.incomeCents) * 100).roundToInt()

/** Neto (prihodki - stroški) za zadnjih [count] mesecev do vključno [month]. */
fun monthlyNetTrend(txs: List<TransactionUi>, month: YearMonth, count: Int = 6): List<Long> {
    val months = (count - 1 downTo 0).map { month.minusMonths(it.toLong()) }
    val net = LongArray(count)
    txs.forEach { tx ->
        if (!tx.confirmed) return@forEach
        val idx = months.indexOf(YearMonth.from(tx.date))
        if (idx >= 0) net[idx] += if (tx.type == TransactionType.INCOME) tx.amountCents else -tx.amountCents
    }
    return net.toList()
}

fun accountBalances(
    accounts: List<AccountEntity>,
    txs: List<TransactionUi>,
    transfers: List<TransferEntity>,
): List<AccountBalance> {
    val sums = HashMap<String, Long>()
    val defaultUid = accounts.defaultAccount()?.uid
    txs.forEach { tx ->
        if (!tx.confirmed) return@forEach
        val key = tx.accountUid ?: defaultUid ?: return@forEach
        sums[key] = (sums[key] ?: 0) + if (tx.type == TransactionType.INCOME) tx.amountCents else -tx.amountCents
    }
    transfers.forEach { t ->
        t.fromAccountUid?.let { sums[it] = (sums[it] ?: 0) - t.fromAmountCents }
        t.toAccountUid?.let { sums[it] = (sums[it] ?: 0) + (t.toAmountCents ?: t.fromAmountCents) }
    }
    return accounts.map { AccountBalance(it, it.initialBalanceCents + (sums[it.uid] ?: 0)) }
}

fun budgetStatuses(categories: List<CategoryEntity>, monthTxs: List<TransactionUi>): List<BudgetStatus> {
    val spent = monthTxs.filter { it.type == TransactionType.EXPENSE && it.confirmed }
        .groupBy { it.categoryUid }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
    return categories.mapNotNull { c ->
        val budget = c.monthlyBudgetCents?.takeIf { it > 0 } ?: return@mapNotNull null
        BudgetStatus(c, spent[c.uid] ?: 0, budget)
    }.sortedByDescending { it.fraction }
}

fun buildGoals(goals: List<SavingsGoalEntity>, contributions: List<GoalContributionEntity>, today: LocalDate): List<GoalUi> {
    val byGoal = contributions.groupBy { it.goalUid }
    val since = today.minusMonths(3)
    return goals.map { g ->
        val list = byGoal[g.uid].orEmpty()
        val saved = list.sumOf { it.amountCents }
        val recent = list.filter { !millisToLocalDate(it.date).isBefore(since) }.sumOf { it.amountCents }
        val perMonth = recent / 3.0
        val remaining = g.targetCents - saved
        val projected = if (remaining > 0 && perMonth > 0) {
            today.plusDays((remaining / perMonth * 30.4).toLong())
        } else null
        GoalUi(g, saved, list, projected)
    }
}

/** Oceni izraz "4,50+2,20-1" v cente; null, če ni veljaven. */
fun evaluateAmountExpression(expr: String): Long? {
    if (expr.isBlank()) return 0
    var total = 0L
    var sign = 1
    val current = StringBuilder()
    fun flush(): Boolean {
        if (current.isEmpty()) return true
        val cents = parseCents(current.toString()) ?: return false
        total += sign * cents
        current.clear()
        return true
    }
    for (ch in expr) {
        when (ch) {
            '+', '-', '−' -> {
                if (!flush()) return null
                sign = if (ch == '+') 1 else -1
            }
            else -> current.append(ch)
        }
    }
    if (!flush()) return null
    return total
}

fun parseCents(text: String): Long? {
    val norm = text.trim().replace(',', '.')
    if (norm.isEmpty()) return 0
    val value = norm.toBigDecimalOrNull() ?: return null
    return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Prenos med računi za prikaz (ni prihodek ne strošek). */
data class TransferUi(
    val uid: String,
    val fromUid: String?,
    val fromTitle: String?,
    val toUid: String?,
    val toTitle: String?,
    val amountCents: Long,
    val date: LocalDate,
    val comment: String,
)

fun TransferEntity.toUi(accountTitles: Map<String, String>) = TransferUi(
    uid = uid,
    fromUid = fromAccountUid, fromTitle = fromAccountUid?.let { accountTitles[it] },
    toUid = toAccountUid, toTitle = toAccountUid?.let { accountTitles[it] },
    amountCents = fromAmountCents,
    date = millisToLocalDate(date),
    comment = comment,
)

/** Predlog ponavljajočega pravila, zaznan iz zgodovine. */
data class SubscriptionSuggestion(
    val title: String,
    val type: TransactionType,
    val amountCents: Long,
    val categoryUid: String?,
    val accountUid: String?,
    val lastDate: LocalDate,
    val months: Int,
) {
    val key: String get() = "${type.name}|${title.lowercase()}|$amountCents"
}

/**
 * Zazna ponavljajoče se transakcije (naročnine, najemnina, plača): isti opis/kategorija,
 * podoben znesek (±10 %), v vsaj 3 različnih mesecih zadnjih 6 mesecev, največ ~1x na mesec.
 * Izpusti tiste, ki jih že pokriva obstoječe pravilo.
 */
fun detectSubscriptions(
    txs: List<TransactionUi>,
    rules: List<RecurringRuleEntity>,
    today: LocalDate,
): List<SubscriptionSuggestion> {
    val since = today.minusMonths(6).withDayOfMonth(1)
    val candidates = txs.filter {
        it.confirmed && !it.date.isBefore(since) &&
            it.source != si.moneo.data.db.entity.TransactionSource.RECURRING &&
            it.source != si.moneo.data.db.entity.TransactionSource.SUBSCRIPTION
    }
    val groups = candidates.groupBy { tx ->
        val key = tx.comment.trim().lowercase().ifEmpty { "cat:" + (tx.categoryUid ?: return@groupBy null) }
        tx.type to key
    }
    val out = mutableListOf<SubscriptionSuggestion>()
    for ((groupKey, list) in groups) {
        if (groupKey == null || list.size < 3) continue
        // Razvrsti po znesku okoli mediane
        val median = list.map { it.amountCents }.sorted()[list.size / 2]
        if (median <= 0) continue
        val similar = list.filter { abs(it.amountCents - median) * 10 <= median }
        val months = similar.map { YearMonth.from(it.date) }.distinct()
        if (months.size < 3) continue
        if (similar.size > months.size * 3 / 2) continue // prepogosto - ni mesečno
        val last = similar.maxBy { it.date }
        val title = last.comment.trim().ifEmpty { last.categoryTitle.orEmpty() }
        if (title.isEmpty()) continue
        val covered = rules.any { r ->
            !r.deleted && r.type == last.type && (
                r.title.equals(title, ignoreCase = true) ||
                    (r.categoryUid != null && r.categoryUid == last.categoryUid && abs(r.amountCents - median) * 10 <= median)
                )
        }
        if (covered) continue
        out += SubscriptionSuggestion(title.replaceFirstChar { it.uppercase() }, last.type, median, last.categoryUid, last.accountUid, last.date, months.size)
    }
    return out.sortedByDescending { it.amountCents }
}

/** Obdobja, ki jih ponuja Domov. */
val HOME_PERIODS = listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR)

/** Obdobja v Statistiki (dan je za grafe premajhen). */
val STATS_PERIODS = listOf(StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.QUARTER, StatsPeriod.YEAR)

/** Kratek naziv obdobja za izbirnik na Domov: "Danes", "Ta teden", "September 2026" ... */
fun homePeriodLabel(period: StatsPeriod, start: LocalDate, today: LocalDate = LocalDate.now()): String = when (period) {
    StatsPeriod.DAY -> when (start) {
        today -> str(R.string.today)
        today.minusDays(1) -> str(R.string.yesterday)
        else -> start.fmt(R.string.fmt_day_short_year, "EEE, d. M. yyyy").replaceFirstChar { it.uppercase() }
    }
    StatsPeriod.WEEK -> {
        when (start) {
            period.startOf(today) -> str(R.string.this_week)
            period.startOf(today).minusWeeks(1) -> str(R.string.last_week)
            else -> weekRange(start)
        }
    }
    else -> period.longLabel(start)
}

/** "Stanje dneva/tedna/meseca/leta". */
fun balanceTitle(period: StatsPeriod): String = when (period) {
    StatsPeriod.DAY -> str(R.string.balance_day)
    StatsPeriod.WEEK -> str(R.string.balance_week)
    StatsPeriod.MONTH -> str(R.string.balance_month)
    StatsPeriod.QUARTER -> str(R.string.balance_quarter)
    StatsPeriod.YEAR -> str(R.string.balance_year)
}

/** Povprečni dnevni prihodek in odhodek. */
data class DailyAverage(val incomeCents: Long, val expenseCents: Long, val days: Long, val note: String)

/**
 * Povprečje na dan za obdobje [start, endExclusive).
 * Tekoče obdobje se deli s pretečenimi dnevi (danes vključno), preteklo s celotno dolžino.
 * Za en sam dan (DAY) povprečje nima smisla - takrat vrne povprečje zadnjih 30 dni do izbranega dne.
 */
fun dailyAverage(
    txs: List<TransactionUi>,
    period: StatsPeriod,
    start: LocalDate,
    endExclusive: LocalDate,
    today: LocalDate,
): DailyAverage {
    val (from, toInclusive, note) = if (period == StatsPeriod.DAY) {
        Triple(start.minusDays(29), start, str(R.string.avg_last_30_days))
    } else {
        val last = minOf(endExclusive.minusDays(1), today)
        Triple(start, last, if (last.isBefore(endExclusive.minusDays(1))) str(R.string.avg_to_date) else "")
    }
    val days = (ChronoUnit.DAYS.between(from, toInclusive) + 1).coerceAtLeast(1)
    var income = 0L
    var expense = 0L
    txs.forEach { tx ->
        if (!tx.confirmed || tx.date.isBefore(from) || tx.date.isAfter(toInclusive)) return@forEach
        if (tx.type == TransactionType.INCOME) income += tx.amountCents else expense += tx.amountCents
    }
    return DailyAverage(
        incomeCents = Math.round(income.toDouble() / days),
        expenseCents = Math.round(expense.toDouble() / days),
        days = days,
        note = note,
    )
}
