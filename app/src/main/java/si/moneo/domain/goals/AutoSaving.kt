package si.moneo.domain.goals

import java.time.LocalDate
import java.time.YearMonth

/** Meseci za samodejno vplačilo v cilj in nova vrednost "zadnji zapisani mesec". */
data class AutoSavingPlan(val months: List<YearMonth>, val lastMonth: YearMonth)

/**
 * Kateri meseci so na vrsti za samodejno vplačilo.
 *  - termin v mesecu je [autoDay] (1-28); mesec je na vrsti, ko je ta dan mimo
 *  - [lastAutoMonth] == null (pravkar vklopljeno): tekoči termin se ne zapiše za nazaj
 *  - zamujeni meseci se nadoknadijo, največ 12 nazaj
 */
fun autoSavingPlan(lastAutoMonth: String?, autoDay: Int, today: LocalDate): AutoSavingPlan {
    val thisMonth = YearMonth.from(today)
    val lastDue = if (today.dayOfMonth >= autoDay.coerceIn(1, 28)) thisMonth else thisMonth.minusMonths(1)
    val last = lastAutoMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: lastDue
    val months = mutableListOf<YearMonth>()
    var month = maxOf(last.plusMonths(1), lastDue.minusMonths(11))
    while (!month.isAfter(lastDue)) {
        months += month
        month = month.plusMonths(1)
    }
    return AutoSavingPlan(months, maxOf(last, lastDue))
}
