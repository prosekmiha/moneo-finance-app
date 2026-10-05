package si.moneo.domain.voice

import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.text.WordMatch
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Parser glasovnih ukazov s fiksnimi pravili (brez LLM, deluje offline).
 *
 * Gramatika (vrstni red je prost, besede se lahko prepletajo):
 *
 *   [tip] [kategorija] [znesek] [račun] [opomba ...] [datum]
 *
 *  - tip:      "dohodek" | "prihodek" | "plus" -> INCOME; "strošek" | "minus" -> EXPENSE;
 *              sicer se sklepa iz kategorije (privzeto EXPENSE)
 *  - znesek:   "12,30" | "12.30" | "12 30" | "12 evrov 30" | "30 centov"
 *              | besede: "dvanajst evrov trideset", "štiriindvajset", "tristo petdeset",
 *                "dva tisoč petsto", "dve cela pet" (= 2,50)
 *  - kategorija: naziv ali ključna beseda kategorije, tudi v drugem sklonu ("v trgovini" -> "trgovina")
 *  - račun:    naziv računa ("z Revolutom"), "gotovina" / "s kartico" -> račun s tem v nazivu
 *  - datum:    "danes" (privzeto) | "včeraj" | "predvčerajšnjim" | "v petek" | "pred tremi dnevi"
 *              | "15. septembra" | "15. 9."
 *  - opomba:   vse preostale besede
 *
 * Primeri:
 *   "malica 4,50"                        -> EXPENSE 4,50 €, Malica, danes
 *   "bencin 30 evrov včeraj"             -> EXPENSE 30,00 €, Bencin, včeraj
 *   "dohodek plača tisoč dvesto"         -> INCOME 1.200,00 €, Plača
 *   "v trgovini štiriindvajset evrov v petek s kartico"
 *                                        -> EXPENSE 24,00 €, kategorija s ključno besedo "trgovina",
 *                                           zadnji petek, račun s "kartica" v nazivu
 */
class VoiceCommandParser(
    private val categories: List<CategoryEntity>,
    private val accounts: List<AccountEntity> = emptyList(),
    private val today: LocalDate = LocalDate.now(),
    private val vocabulary: VoiceVocabulary = VoiceVocabulary(),
) {

    data class Parsed(
        val rawText: String,
        val type: TransactionType,
        val amountCents: Long?,
        val category: CategoryEntity?,
        val date: LocalDate,
        val comment: String,
        /** Besede, ki jih parser ni razumel (za povratno informacijo uporabniku). */
        val unrecognized: List<String>,
        /** Račun, omenjen v stavku (null = privzeti račun). */
        val account: AccountEntity? = null,
    ) {
        val isComplete: Boolean get() = amountCents != null
    }

    fun parse(rawText: String): Parsed {
        val tokens = normalize(rawText).split(' ').filter { it.isNotBlank() }
        val consumed = mutableSetOf<Int>()

        // 1) Tip transakcije (eksplicitni označevalec; sicer se sklepa iz kategorije)
        var explicitType: TransactionType? = null
        tokens.forEachIndexed { i, t ->
            when (t) {
                "dohodek", "prihodek", "plus" -> { explicitType = TransactionType.INCOME; consumed += i }
                "strosek", "minus" -> { explicitType = TransactionType.EXPENSE; consumed += i }
                in vocabulary.income -> { explicitType = TransactionType.INCOME; consumed += i }
                in vocabulary.expense -> { explicitType = TransactionType.EXPENSE; consumed += i }
            }
        }

        // 2) Datum (pred zneskom, da "15. septembra" ne postane znesek)
        val date = parseDate(tokens, consumed) ?: today

        // 3) Znesek: najprej števke, nato številske besede
        val amountCents = parseDigits(tokens, consumed) ?: parseNumberWords(tokens, consumed)

        // 4) Račun
        val account = parseAccount(tokens, consumed)

        // 5) Kategorija (najdaljše ujemanje po nazivu ali ključnih besedah, upošteva sklone)
        var category: CategoryEntity? = null
        var bestLen = 0
        var bestIdx = -1
        for (cat in categories) {
            for (alias in WordMatch.aliases(cat)) {
                if (alias.size <= bestLen) continue
                val idx = WordMatch.indexOfPhrase(tokens, alias, consumed)
                if (idx >= 0) {
                    category = cat
                    bestLen = alias.size
                    bestIdx = idx
                }
            }
        }
        if (bestIdx >= 0) (0 until bestLen).forEach { consumed += bestIdx + it }

        // 6) Tip: eksplicitni označevalec ima prednost, sicer sklepamo iz kategorije
        val type = explicitType ?: category?.type ?: TransactionType.EXPENSE

        // 7) Opomba = preostale besede (brez valutnih in veznih besed)
        val rest = tokens.filterIndexed { i, _ -> i !in consumed }
        val comment = rest.filter { it !in FILLER && it !in CURRENCY && it !in CENTS }.joinToString(" ").trim()

        return Parsed(
            rawText = rawText,
            type = type,
            amountCents = amountCents,
            category = category,
            date = date,
            comment = comment,
            unrecognized = if (amountCents == null) rest else emptyList(),
            account = account,
        )
    }

    // ---------------- Datum ----------------

    private fun parseDate(tokens: List<String>, consumed: MutableSet<Int>): LocalDate? {
        // Besede v jeziku aplikacije (lahko več besed, npr. danski "i går")
        for ((phrases, days) in listOf(vocabulary.today to 0L, vocabulary.yesterday to 1L)) {
            for (phrase in phrases) {
                val idx = WordMatch.indexOfPhrase(tokens, phrase, consumed)
                if (idx >= 0) {
                    phrase.indices.forEach { consumed += idx + it }
                    return today.minusDays(days)
                }
            }
        }
        tokens.forEachIndexed { i, t ->
            val relative = when (t) {
                "danes" -> 0L
                "vceraj" -> 1L
                "predvcerajsnjim" -> 2L
                else -> null
            }
            if (relative != null) {
                consumed += i
                return today.minusDays(relative)
            }
        }
        // "pred tremi dnevi", "pred 3 dnevi", "pred enim tednom"
        tokens.forEachIndexed { i, t ->
            if (t != "pred" || i + 2 >= tokens.size) return@forEachIndexed
            val n = tokens[i + 1].toIntOrNull() ?: INSTRUMENTAL[tokens[i + 1]] ?: return@forEachIndexed
            val unit = tokens[i + 2]
            val days = when {
                unit.startsWith("dn") -> n.toLong()
                unit.startsWith("tedn") -> n * 7L
                else -> return@forEachIndexed
            }
            consumed += listOf(i, i + 1, i + 2)
            return today.minusDays(days)
        }
        // "pred tednom" / "pred dnevom"
        tokens.forEachIndexed { i, t ->
            if (t == "pred" && i + 1 < tokens.size) {
                val days = when {
                    tokens[i + 1].startsWith("tedn") -> 7L
                    tokens[i + 1].startsWith("dnev") -> 1L
                    else -> null
                }
                if (days != null) {
                    consumed += listOf(i, i + 1)
                    return today.minusDays(days)
                }
            }
        }
        // "15. septembra" / "15 septembra 2025"
        tokens.forEachIndexed { i, t ->
            val day = Regex("""(\d{1,2})\.?""").matchEntire(t)?.groupValues?.get(1)?.toInt() ?: return@forEachIndexed
            val month = tokens.getOrNull(i + 1)?.let { m -> MONTHS.entries.firstOrNull { m.startsWith(it.key) }?.value } ?: return@forEachIndexed
            val year = tokens.getOrNull(i + 2)?.let { Regex("""(\d{4})\.?""").matchEntire(it)?.groupValues?.get(1)?.toInt() }
            val d = safeDate(year ?: today.year, month, day) ?: return@forEachIndexed
            consumed += i; consumed += i + 1
            if (year != null) consumed += i + 2
            return if (year == null && d.isAfter(today)) d.minusYears(1) else d
        }
        // "15. 9." (obe števki s piko) ali "15.9."
        tokens.forEachIndexed { i, t ->
            Regex("""(\d{1,2})\.(\d{1,2})\.""").matchEntire(t)?.let { m ->
                val d = safeDate(today.year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) ?: return@let
                consumed += i
                return if (d.isAfter(today)) d.minusYears(1) else d
            }
            val a = Regex("""(\d{1,2})\.""").matchEntire(t) ?: return@forEachIndexed
            val b = tokens.getOrNull(i + 1)?.let { Regex("""(\d{1,2})\.""").matchEntire(it) } ?: return@forEachIndexed
            val d = safeDate(today.year, b.groupValues[1].toInt(), a.groupValues[1].toInt()) ?: return@forEachIndexed
            consumed += i; consumed += i + 1
            return if (d.isAfter(today)) d.minusYears(1) else d
        }
        // "v petek", "prejšnjo sredo" -> zadnji tak dan (danes, če je danes ta dan)
        tokens.forEachIndexed { i, t ->
            val dow = WEEKDAYS.entries.firstOrNull { t.startsWith(it.key) }?.value ?: return@forEachIndexed
            consumed += i
            if (i > 0 && tokens[i - 1] in setOf("v", "prejsnji", "prejsnjo", "zadnji", "zadnjo")) consumed += i - 1
            var d = today
            while (d.dayOfWeek != dow) d = d.minusDays(1)
            // "prejšnji petek" na petek pomeni teden nazaj
            if (d == today && i > 0 && tokens[i - 1].startsWith("prejsnj")) d = d.minusWeeks(1)
            return d
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    // ---------------- Znesek ----------------

    /** "12,30", "12.30", "12 30", "12 evrov 30", "30 centov". */
    private fun parseDigits(tokens: List<String>, consumed: MutableSet<Int>): Long? {
        val digitRegex = Regex("""(\d{1,7})(?:[,.](\d{1,2}))?""")
        tokens.forEachIndexed { i, t ->
            if (i in consumed) return@forEachIndexed
            // pika na koncu stavka ("kava 2,50.")
            val m = digitRegex.matchEntire(t.trimEnd('.')) ?: return@forEachIndexed
            val whole = m.groupValues[1].toLong()
            var frac = m.groupValues[2]
            consumed += i
            val next = tokens.getOrNull(i + 1)
            when {
                // "30 centov"
                frac.isEmpty() && next in CENTS && whole < 100 -> { consumed += i + 1; return whole }
                // "12 30"
                frac.isEmpty() && next != null && i + 1 !in consumed && next.matches(Regex("""\d{1,2}""")) -> {
                    frac = next; consumed += i + 1
                }
                // "12 evrov 30 (centov)"
                frac.isEmpty() && next in CURRENCY -> {
                    consumed += i + 1
                    val after = tokens.getOrNull(i + 2)
                    if (after != null && after.matches(Regex("""\d{1,2}"""))) {
                        frac = after.padStart(2, '0'); consumed += i + 2
                        if (tokens.getOrNull(i + 3) in CENTS) consumed += i + 3
                    }
                }
            }
            return whole * 100 + (if (frac.isEmpty()) 0 else frac.padEnd(2, '0').toLong())
        }
        return null
    }

    /**
     * Številske besede: "štiriindvajset", "tristo petdeset", "dva tisoč petsto",
     * "dvanajst evrov trideset (centov)", "dve cela pet" (= 2,50). Vrne znesek v centih ali null.
     */
    private fun parseNumberWords(tokens: List<String>, consumed: MutableSet<Int>): Long? {
        val start = tokens.indices.firstOrNull { it !in consumed && (numberWord(tokens[it]) != null || tokens[it] == "tisoc") }
            ?: return null
        var total = 0L
        var group = 0L
        var cents = -1L
        var decimal = false
        var inCents = false
        val used = mutableListOf<Int>()
        var i = start
        while (i < tokens.size && i !in consumed) {
            val t = tokens[i]
            val v = numberWord(t)
            when {
                t in CURRENCY -> { inCents = true; used += i }
                t in DECIMAL -> { inCents = true; decimal = true; used += i }
                // "petdeset centov" brez evrov: dosedanje število so centi
                t in CENTS -> {
                    if (!inCents) { cents = group; group = 0 }
                    used += i; i++; break
                }
                t == "in" && used.isNotEmpty() -> used += i
                t == "tisoc" && !inCents -> { total += (if (group == 0L) 1 else group) * 1000; group = 0; used += i }
                t.endsWith("tisoc") && !inCents && numberWord(t.removeSuffix("tisoc")) != null -> {
                    total += numberWord(t.removeSuffix("tisoc"))!! * 1000; used += i
                }
                v != null && inCents -> { cents = (if (cents < 0) 0 else cents) + v; used += i }
                v != null -> { group += v; used += i }
                else -> break
            }
            i++
        }
        val whole = total + group
        if (whole == 0L && cents <= 0) return null
        consumed += used
        // "dve cela pet" = 2,50 (desetinke), "dve evri pet" = 2,05
        val c = when {
            cents < 0 -> 0L
            decimal && cents < 10 -> cents * 10
            else -> cents.coerceAtMost(99)
        }
        return whole * 100 + c
    }

    /** Vrednost ene številske besede ("pet", "štiriindvajset", "tristo") ali null. */
    private fun numberWord(t: String): Long? {
        UNITS[t]?.let { return it.toLong() }
        TEENS[t]?.let { return it.toLong() }
        TENS[t]?.let { return it.toLong() }
        // "stiriindvajset" = 4 + 20
        val inIdx = t.indexOf("in")
        if (inIdx > 0) {
            val unit = COMPOUND_UNITS[t.substring(0, inIdx)]
            val ten = TENS[t.substring(inIdx + 2)]
            if (unit != null && ten != null) return (unit + ten).toLong()
        }
        // "sto", "dvesto", "tristo" ... "devetsto"
        if (t.endsWith("sto")) {
            val prefix = t.removeSuffix("sto")
            if (prefix.isEmpty()) return 100
            HUNDREDS_PREFIX[prefix]?.let { return it * 100L }
        }
        return null
    }

    // ---------------- Račun ----------------

    private fun parseAccount(tokens: List<String>, consumed: MutableSet<Int>): AccountEntity? {
        if (accounts.size < 2) return null
        var best: AccountEntity? = null
        var bestIdx = -1
        for (acc in accounts) {
            val words = WordMatch.tokens(acc.title).filter { it.length >= 4 && it !in GENERIC_ACCOUNT_WORDS }
            for (w in words) {
                val idx = tokens.indices.firstOrNull { it !in consumed && WordMatch.sameWord(tokens[it], w) } ?: continue
                best = acc; bestIdx = idx
            }
        }
        // "gotovina", "s kartico" -> račun s tem v nazivu
        if (best == null) {
            tokens.forEachIndexed { i, t ->
                if (i in consumed || best != null) return@forEachIndexed
                val hint = ACCOUNT_HINTS.entries.firstOrNull { (words, _) -> words.any { WordMatch.sameWord(t, it) } }?.value
                    ?: return@forEachIndexed
                accounts.firstOrNull { a -> hint.any { WordMatch.normalize(a.title).contains(it) } }?.let { best = it; bestIdx = i }
            }
        }
        if (bestIdx >= 0) {
            consumed += bestIdx
            if (bestIdx > 0 && tokens[bestIdx - 1] in setOf("s", "z", "iz", "na", "prek", "preko")) consumed += bestIdx - 1
        }
        return best
    }

    // ------------------------------------------------------------------

    /** Kot [WordMatch.normalize], a ohrani vejice in pike (decimalke, datumi). */
    private fun normalize(text: String): String = WordMatch.fold(text)
        .replace('€', ' ')
        .replace(Regex("""[^\p{L}\p{N},. ]"""), " ")
        .replace(Regex("""(?<!\d)[,.]|[,.](?!\d|$| )"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private companion object {
        val UNITS = mapOf(
            "nic" to 0, "ena" to 1, "en" to 1, "eno" to 1, "dva" to 2, "dve" to 2, "tri" to 3, "trije" to 3,
            "stiri" to 4, "pet" to 5, "sest" to 6, "sedem" to 7, "osem" to 8, "devet" to 9,
        )
        val COMPOUND_UNITS = mapOf(
            "ena" to 1, "en" to 1, "dva" to 2, "tri" to 3, "stiri" to 4, "pet" to 5,
            "sest" to 6, "sedem" to 7, "osem" to 8, "devet" to 9,
        )
        val TEENS = mapOf(
            "deset" to 10, "enajst" to 11, "dvanajst" to 12, "trinajst" to 13, "stirinajst" to 14,
            "petnajst" to 15, "sestnajst" to 16, "sedemnajst" to 17, "osemnajst" to 18, "devetnajst" to 19,
        )
        val TENS = mapOf(
            "dvajset" to 20, "trideset" to 30, "stirideset" to 40, "petdeset" to 50,
            "sestdeset" to 60, "sedemdeset" to 70, "osemdeset" to 80, "devetdeset" to 90,
        )
        val HUNDREDS_PREFIX = mapOf(
            "dve" to 2, "tri" to 3, "stiri" to 4, "pet" to 5, "ses" to 6, "sest" to 6,
            "sedem" to 7, "osem" to 8, "devet" to 9,
        )
        /** "pred tremi dnevi" */
        val INSTRUMENTAL = mapOf(
            "enim" to 1, "enem" to 1, "dvema" to 2, "tremi" to 3, "stirimi" to 4, "petimi" to 5,
            "sestimi" to 6, "sedmimi" to 7, "osmimi" to 8, "devetimi" to 9, "desetimi" to 10,
        )
        val CURRENCY = setOf("evrov", "evre", "evra", "evri", "evr", "evro", "eur", "eura", "eurov", "euro", "euri", "euros")
        val CENTS = setOf("centov", "cente", "centa", "centi", "cent")
        val DECIMAL = setOf("cela", "celih", "cel", "vejica")
        val FILLER = setOf("in", "za", "na", "s", "z", "iz", "dne")
        val MONTHS = linkedMapOf(
            "januar" to 1, "februar" to 2, "marc" to 3, "marec" to 3, "april" to 4, "maj" to 5, "junij" to 6,
            "julij" to 7, "avgust" to 8, "septemb" to 9, "oktob" to 10, "novemb" to 11, "decemb" to 12,
        )
        val WEEKDAYS = mapOf(
            "ponedelj" to DayOfWeek.MONDAY, "torek" to DayOfWeek.TUESDAY, "sred" to DayOfWeek.WEDNESDAY,
            "cetrt" to DayOfWeek.THURSDAY, "petek" to DayOfWeek.FRIDAY, "petka" to DayOfWeek.FRIDAY,
            "sobot" to DayOfWeek.SATURDAY, "nedelj" to DayOfWeek.SUNDAY,
        )
        /** Besede v nazivih računov, ki same po sebi ne povedo, kateri račun (npr. "Glavni račun"). */
        val GENERIC_ACCOUNT_WORDS = setOf("racun", "racuna", "glavni", "moj", "moja")
        val ACCOUNT_HINTS = mapOf(
            listOf("gotovina", "gotovino", "kes", "kesh", "cash") to listOf("gotov", "cash"),
            listOf("kartica", "kartico", "karticami") to listOf("kartic", "card"),
        )
    }
}

/**
 * Dodatne besede v jeziku aplikacije (npr. "income", "yesterday"); slovenske so vgrajene v parser.
 * Besede so ločene z vejico, primerjajo se normalizirane.
 */
data class VoiceVocabulary(
    val income: Set<String> = emptySet(),
    val expense: Set<String> = emptySet(),
    val today: List<List<String>> = emptyList(),
    val yesterday: List<List<String>> = emptyList(),
) {
    companion object {
        fun of(income: String, expense: String, today: String, yesterday: String) =
            VoiceVocabulary(words(income), words(expense), phrases(today), phrases(yesterday))

        private fun words(list: String): Set<String> =
            list.split(',').map { WordMatch.normalize(it) }.filter { it.isNotEmpty() && ' ' !in it }.toSet()

        private fun phrases(list: String): List<List<String>> =
            list.split(',').map { WordMatch.tokens(it) }.filter { it.isNotEmpty() }.distinct()
    }
}
