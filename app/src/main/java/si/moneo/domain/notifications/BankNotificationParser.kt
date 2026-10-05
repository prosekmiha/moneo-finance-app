package si.moneo.domain.notifications

/**
 * Razčlenjevalnik bančnih obvestil (brez Android odvisnosti).
 *
 * Prepozna:
 *  - zneske v obeh zapisih: "1.234,56", "1,234.56", "12,34", "12.34", "12 €", "€12.34", "EUR 12,34"
 *  - smer: slovenske in angleške ključne besede (plačilo, bremenitev, nakup, priliv, prejem, paid, received ...)
 *  - trgovca: besedilo za "pri" / "at" / "v" ali za zneskom
 *
 * Obvestilo brez ključne besede za smer IN brez zneska z valuto se ignorira (manj lažnih zadetkov).
 */
object BankNotificationParser {

    data class Result(val amountCents: Long, val isIncome: Boolean, val merchant: String)

    private val INCOME = listOf(
        "prejem", "prejeli", "prejeto", "polog", "dobroimetje", "dobropis", "priliv", "nakazilo prejeto",
        "vračilo", "vracilo", "received", "refund", "top-up", "top up", "topped up", "deposit", "incoming",
    )
    private val EXPENSE = listOf(
        "plačilo", "placilo", "plačali", "bremenitev", "nakup", "odliv", "dvig", "pos", "transakcija s kartico",
        "kartica", "paid", "payment", "spent", "purchase", "withdrawal", "card transaction",
    )

    // Znesek z valuto pred ali za številom
    private const val NUM = """\d{1,3}(?:[.,\s]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""
    private val AMOUNT = Regex("""(?:(?:€|eur)\s*($NUM))|(?:($NUM)\s*(?:€|eur\b))""", RegexOption.IGNORE_CASE)
    private val MERCHANT = Regex("""(?:\bpri\b|\bat\b|\bv\b|\bto\b|\bfrom\b|\bod\b)\s+([\p{L}0-9][\p{L}0-9 &.'\-*]{1,40})""", RegexOption.IGNORE_CASE)

    fun parse(title: String, text: String): Result? {
        val content = "$title\n$text"
        val lower = content.lowercase()
        val income = INCOME.any { lower.containsWord(it) }
        val expense = EXPENSE.any { lower.containsWord(it) }
        if (!income && !expense) return null

        val m = AMOUNT.find(content) ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val cents = parseAmount(raw) ?: return null
        if (cents <= 0) return null

        // Če se pojavita obe smeri (npr. "vračilo plačila"), odloči prva omemba
        val isIncome = if (income && expense) {
            val firstIncome = INCOME.mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull() ?: Int.MAX_VALUE
            val firstExpense = EXPENSE.mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull() ?: Int.MAX_VALUE
            firstIncome < firstExpense
        } else income

        val merchant = MERCHANT.find(content.substring(m.range.last + 1))?.groupValues?.get(1)
            ?: MERCHANT.find(content)?.groupValues?.get(1)
            ?: content.substring(m.range.last + 1).trim().lineSequence().firstOrNull().orEmpty()
        return Result(cents, isIncome, merchant.trim().trimEnd('.', ',').take(60))
    }

    /** "1.234,56" / "1,234.56" / "12,5" / "12" -> centi. Zadnje ločilo z 1-2 števkama je decimalno. */
    fun parseAmount(raw: String): Long? {
        val s = raw.replace(" ", "").replace(" ", "")
        val lastSep = s.indexOfLast { it == '.' || it == ',' }
        val (intPart, decPart) = if (lastSep >= 0 && s.length - lastSep - 1 in 1..2) {
            s.substring(0, lastSep) to s.substring(lastSep + 1)
        } else s to ""
        val digits = intPart.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val euros = digits.toLongOrNull() ?: return null
        val cents = decPart.padEnd(2, '0').toLongOrNull() ?: 0
        return euros * 100 + cents
    }

    /** Začetek besede (dovoli sklone: "plačilo" -> "plačila"); kratke besede (npr. "pos") morajo biti cele. */
    private fun String.containsWord(word: String): Boolean {
        val stem = if (word.length > 4) word.dropLast(1) else word
        val end = if (word.length <= 4) """(?![\p{L}])""" else ""
        return Regex("""(?<![\p{L}])${Regex.escape(stem)}$end""").containsMatchIn(this)
    }
}
