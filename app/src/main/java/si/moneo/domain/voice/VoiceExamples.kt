package si.moneo.domain.voice

import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.domain.text.WordMatch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Primer stavka za glasovni vnos in druge besede, ki prav tako izberejo to kategorijo. */
data class VoiceExample(val phrase: String, val categoryTitle: String, val alternatives: List<String>)

/** Privzeti primeri, ko uporabnik še nima zgodovine vnosov. */
val DEFAULT_VOICE_EXAMPLES = listOf(
    VoiceExample("malica 4,50", "", emptyList()),
    VoiceExample("bencin 30 evrov včeraj", "", emptyList()),
    VoiceExample("prihodek plača tisoč dvesto", "", emptyList()),
)

/**
 * Primeri glasovnih ukazov za uporabnikove najpogostejše kategorije zadnjih dveh mesecev,
 * z zneskom, ki ga pri kategoriji najpogosteje vnaša (mediana). Vsak primer se preveri s
 * [VoiceCommandParser] - pokažejo se le stavki, ki jih parser res pravilno razume.
 */
fun voiceExamples(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    today: LocalDate = LocalDate.now(),
    count: Int = 3,
    /** Slovenski primeri z "včeraj", "z gotovino" in zneskom v evrih; v drugih jezikih le naziv in znesek. */
    slovenian: Boolean = true,
    defaults: List<VoiceExample> = DEFAULT_VOICE_EXAMPLES,
    decimalSeparator: Char = ',',
): List<VoiceExample> {
    val since = today.minusMonths(2)
    val zone = ZoneId.systemDefault()
    val recent = transactions.filter {
        !it.deleted && it.confirmed && it.categoryUid != null &&
            !Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().isBefore(since)
    }
    val byCategory = recent.groupBy { it.categoryUid!! }
    val top = byCategory.entries.sortedByDescending { it.value.size }
        .mapNotNull { (uid, list) -> categories.firstOrNull { it.uid == uid && !it.deleted }?.let { it to list } }
        .take(count)
    if (top.isEmpty()) return defaults

    val parser = VoiceCommandParser(categories, accounts, today)
    val activeAccounts = accounts.filter { !it.deleted }
    val accountPhrase = when {
        activeAccounts.size < 2 -> null
        activeAccounts.any { WordMatch.normalize(it.title).contains("gotov") } -> "z gotovino"
        activeAccounts.any { WordMatch.normalize(it.title).contains("kartic") } -> "s kartico"
        else -> null
    }
    // Vsak primer pokaže še eno možnost: brez dodatka, datum, račun ali dan v tednu
    val extras = if (slovenian) listOf(null, "včeraj", accountPhrase ?: "v petek") else emptyList()

    return top.mapIndexedNotNull { i, (cat, list) ->
        val amounts = list.map { it.amountCents }.sorted()
        val cents = amounts[amounts.size / 2]
        val amountText = when {
            cents % 100 == 0L -> if (slovenian) "${cents / 100} evrov" else "${cents / 100} €"
            else -> "%d%s%02d".format(cents / 100, decimalSeparator, cents % 100)
        }
        val extra = extras.getOrNull(i)
        // Najprej naziv, nato ključne besede - prva beseda, ki jo parser razume kot to kategorijo.
        // Tuj naziv (npr. "Groceries") prepoznava govora v slovenščini ne zapiše pravilno, zato
        // ima tam prednost slovenska ključna beseda.
        // (naziv je pogosto tudi med ključnimi besedami, zato gredo vse tuje besede na konec - stabilno razvrščanje)
        val words = (listOf(cat.title) + cat.keywords.split(',').map { it.trim() })
            .filter { it.isNotEmpty() }
            .distinctBy { WordMatch.normalize(it) }
            .sortedBy { slovenian && looksForeign(it) }
        val working = words.filter { w ->
            val p = parser.parse(listOfNotNull(w.lowercase(), amountText, extra).joinToString(" "))
            p.category?.uid == cat.uid && p.amountCents == cents
        }
        val word = working.firstOrNull() ?: return@mapIndexedNotNull null
        VoiceExample(
            phrase = listOfNotNull(word.lowercase(), amountText, extra).joinToString(" "),
            categoryTitle = cat.title,
            alternatives = working.drop(1).map { it.lowercase() }.take(3),
        )
    }.ifEmpty { defaults }
}

/**
 * Groba ocena, ali beseda ni slovenska: črke, ki jih slovenska abeceda nima (q, w, x, y),
 * podvojene črke (oo, ee, ff ...) ali tipične angleške končnice.
 */
internal fun looksForeign(word: String): Boolean {
    val w = WordMatch.normalize(word)
    val endings = listOf("ies", "ing", "ment", "tion")
    return w.any { it in "qwxy" } ||
        listOf("oo", "ee", "ff", "ll", "ss", "tt", "pp", "gg", "cc", "th", "sh", "ch").any { w.contains(it) } ||
        w.split(' ').any { part -> endings.any { part.endsWith(it) } }
}
