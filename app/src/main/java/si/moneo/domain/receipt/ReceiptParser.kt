package si.moneo.domain.receipt

import si.moneo.domain.text.WordMatch
import java.time.LocalDate
import kotlin.math.abs

/** Ena vrstica besedila z ML Kit in njen položaj na sliki (v slikovnih točkah). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** Kar se je dalo prebrati z računa. */
data class ReceiptResult(
    /** Najverjetnejši skupni znesek (null = ni najden). */
    val totalCents: Long?,
    /** Drugi zneski z računa (padajoče), da uporabnik hitro izbere pravega. */
    val candidates: List<Long>,
    /** Datum z računa (null = ni najden ali ni verjeten). */
    val date: LocalDate?,
    /** Ime trgovine z vrha računa. */
    val merchant: String?,
)

/**
 * Razčlenjevalnik računov (slovenski in tuji računi), brez omrežja:
 *  - vrstice z ML Kit poveže v vrstice računa po položaju ("SKUPAJ" levo + "23,40" desno)
 *  - skupni znesek: vrstica s "SKUPAJ / ZA PLAČILO / TOTAL", brez DDV, gotovine, vračila ...
 *  - datum računa, ime trgovine z vrha
 */
object ReceiptParser {

    /**
     * Vrstice, ki ležijo na isti višini, združi v eno vrstico računa (od leve proti desni).
     * ML Kit levi stolpec (opis) in desni stolpec (znesek) pogosto vrne kot ločeni vrstici.
     */
    fun rows(lines: List<OcrLine>): List<String> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val sameRow = row != null && row.any { abs(it.centerY - line.centerY) <= minOf(it.height, line.height) / 2 }
            if (sameRow) row!!.add(line) else rows += mutableListOf(line)
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    fun parse(rows: List<String>, today: LocalDate = LocalDate.now()): ReceiptResult {
        val clean = rows.map { it.trim() }.filter { it.isNotEmpty() }
        val norm = clean.map { WordMatch.normalize(it) }

        fun isExcluded(i: Int) = EXCLUDE.any { norm[i].contains(it) }
        fun isTotal(i: Int) = TOTAL_HINTS.any { norm[i].contains(it) }

        val amounts = clean.map { amountsIn(it) }

        // 1) Vrstice s "SKUPAJ / ZA PLAČILO" (brez DDV, gotovine ...); prednost ima "za plačilo"
        val totalRows = clean.indices.filter { isTotal(it) && !isExcluded(it) }
        fun rowAmount(i: Int): Long? =
            amounts[i].lastOrNull()
                // Znesek je včasih v naslednji vrstici (ločena oznaka in znesek)
                ?: (i + 1..minOf(i + 2, clean.lastIndex)).firstOrNull { j -> amounts[j].isNotEmpty() && !norm[j].any { it.isLetter() } }
                    ?.let { amounts[it].last() }
        val payRow = totalRows.firstOrNull { i -> PAY_HINTS.any { norm[i].contains(it) } && rowAmount(i) != null }
        val total = payRow?.let { rowAmount(it) }
            ?: totalRows.mapNotNull { rowAmount(it) }.maxOrNull()
            // 2) sicer največji znesek izven izključenih vrstic (gotovina, vračilo, DDV ...)
            ?: clean.indices.filter { !isExcluded(it) }.flatMap { amounts[it] }.maxOrNull()
            ?: amounts.flatten().maxOrNull()

        val candidates = amounts.flatten().filter { it > 0 }.distinct().sortedDescending()
            .filter { it != total }.take(5)

        return ReceiptResult(
            totalCents = total,
            candidates = candidates,
            date = findDate(clean, today),
            merchant = findMerchant(clean),
        )
    }

    // ---------------- Zneski ----------------

    /** Vsi zneski z dvema decimalkama v vrstici: "23,40", "23.40", "1.234,56", "1 234,56", "1,234.56". */
    fun amountsIn(text: String): List<Long> =
        AMOUNT.findAll(text).mapNotNull { m ->
            val raw = m.value
            // decimalno ločilo je zadnji znak pred zadnjima dvema števkama
            val whole = raw.dropLast(3).filter { it.isDigit() }
            val cents = raw.takeLast(2)
            if (whole.isEmpty() || whole.length > 7) null else whole.toLong() * 100 + cents.toLong()
        }.toList()

    // ---------------- Datum ----------------

    fun findDate(rows: List<String>, today: LocalDate): LocalDate? {
        for (row in rows) {
            for (m in DATE.findAll(row)) {
                val (d, mo, y) = m.destructured
                val year = y.toInt().let { if (it < 100) 2000 + it else it }
                val date = runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull() ?: continue
                // Le verjetni datumi: ne v prihodnosti in ne starejši od enega leta
                if (!date.isAfter(today) && date.isAfter(today.minusYears(1))) return date
            }
        }
        return null
    }

    // ---------------- Trgovina ----------------

    /** Ime trgovine: prva smiselna vrstica na vrhu računa (brez "RAČUN", davčne številke, naslova ...). */
    fun findMerchant(rows: List<String>): String? =
        rows.take(6).firstNotNullOfOrNull { row ->
            val n = WordMatch.normalize(row)
            val letters = row.count { it.isLetter() }
            val digits = row.count { it.isDigit() }
            when {
                letters < 3 || digits > letters -> null
                MERCHANT_SKIP.any { n.startsWith(it) || n.contains(" $it") } -> null
                else -> row
                    .replace(Regex("""(?i)\b(d\.?\s?o\.?\s?o\.?|d\.?\s?d\.?|s\.?\s?p\.?)\s*$"""), "")
                    .trim(' ', ',', '.', '-')
                    .take(40)
                    .let(::titleCaseIfUpper)
                    .ifBlank { null }
            }
        }

    /** "MERCATOR" -> "Mercator", "HOFER TRGOVINA" -> "Hofer Trgovina"; mešane črke ostanejo. */
    private fun titleCaseIfUpper(s: String): String =
        if (s.any { it.isLowerCase() }) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    // ------------------------------------------------------------------

    private val AMOUNT = Regex(
        """(?<![\d.,])(?:\d{1,3}(?:[. ]\d{3})+,\d{2}|\d{1,3}(?:,\d{3})+\.\d{2}|\d{1,7}[.,]\d{2})(?![\d.,]*\d)""",
    )
    private val DATE = Regex("""(?<!\d)(\d{1,2})[./-]\s?(\d{1,2})[./-]\s?(\d{4}|\d{2})(?!\d)""")

    private val TOTAL_HINTS = listOf(
        "skupaj", "za placilo", "za placati", "total", "znesek", "vsota", "skupni", "summe", "suma", "ukupno",
    )
    private val PAY_HINTS = listOf("za placilo", "za placati", "skupaj za placilo", "total due")
    private val EXCLUDE = listOf(
        "ddv", "davek", "osnova", "gotovina", "cash", "vracilo", "drobiz", "placano", "prejeto", "popust",
        "tock", "kartic", "vat", "tax", "mwst", "pdv", "brez ddv",
    )
    private val MERCHANT_SKIP = listOf(
        "racun", "davcna", "id za ddv", "ddv", "st ", "datum", "blagajn", "naslov", "tel ", "telefon", "www", "maticna", "kopija",
    )
}
