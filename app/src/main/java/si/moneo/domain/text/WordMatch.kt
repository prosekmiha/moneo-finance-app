package si.moneo.domain.text

import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType

/**
 * Primerjava besed, ki upošteva slovenske sklone in šumnike:
 * "trgovina" = "trgovini" = "trgovino", "hofer" = "hoferju", "kava" = "kavo", "šport" = "sport".
 */
object WordMatch {

    /** Male črke, brez šumnikov in ločil. */
    fun normalize(text: String): String = fold(text)
        .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Male črke brez naglasov in strešic (č -> c, ä -> a, é -> e ...), za vse jezike. */
    fun fold(text: String): String = java.text.Normalizer
        .normalize(
            text.lowercase().replace("ß", "ss").replace('đ', 'd').replace('ł', 'l').replace('ø', 'o').replace("æ", "ae"),
            java.text.Normalizer.Form.NFD,
        )
        .replace(Regex("""\p{Mn}+"""), "")

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Isti koren: enaki besedi ali (obe vsaj 4 črke) skupni začetek, ki se razlikuje le v
     * kratki končnici (največ 3 črke). Kratke besede (npr. "pet", "tri") se morajo ujemati v celoti.
     */
    fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        if (kotlin.math.abs(a.length - b.length) > 3) return false
        val prefix = a.commonPrefixWith(b).length
        val shorter = minOf(a.length, b.length)
        return prefix >= maxOf(3, shorter - 2)
    }

    /** Indeks, kjer se [phrase] (več besed) pojavi v [tokens], ali -1. [skip] so že porabljeni indeksi. */
    fun indexOfPhrase(tokens: List<String>, phrase: List<String>, skip: Set<Int> = emptySet()): Int {
        if (phrase.isEmpty()) return -1
        outer@ for (start in 0..tokens.size - phrase.size) {
            for (j in phrase.indices) {
                val i = start + j
                if (i in skip || !sameWord(tokens[i], phrase[j])) continue@outer
            }
            return start
        }
        return -1
    }

    /** Besede (fraze) za kategorijo: naziv + ključne besede. */
    fun aliases(category: CategoryEntity): List<List<String>> =
        (category.keywords.split(',') + category.title)
            .map { tokens(it) }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Kategorija, katere naziv ali ključna beseda se pojavi v besedilu (najdaljše ujemanje zmaga). */
    fun matchCategory(text: String, categories: List<CategoryEntity>, type: TransactionType? = null): CategoryEntity? {
        val words = tokens(text)
        if (words.isEmpty()) return null
        var best: CategoryEntity? = null
        var bestLen = 0
        for (cat in categories) {
            if (type != null && cat.type != type) continue
            for (alias in aliases(cat)) {
                if (alias.size > bestLen && indexOfPhrase(words, alias) >= 0) {
                    best = cat
                    bestLen = alias.size
                }
            }
        }
        return best
    }
}
