package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.voice.DEFAULT_VOICE_EXAMPLES
import si.moneo.domain.voice.VoiceCommandParser
import si.moneo.domain.voice.voiceExamples
import java.time.LocalDate
import java.time.ZoneId

class VoiceExamplesTest {

    private val today = LocalDate.of(2026, 9, 30)
    private fun LocalDate.millis() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val categories = listOf(
        CategoryEntity(uid = "g", title = "Groceries", type = TransactionType.EXPENSE, keywords = "groceries,trgovina,lidl"),
        CategoryEntity(uid = "m", title = "Malica", type = TransactionType.EXPENSE, keywords = "malica,kosilo"),
        CategoryEntity(uid = "b", title = "Bencin", type = TransactionType.EXPENSE, keywords = "bencin,petrol"),
    )
    private val accounts = listOf(AccountEntity(uid = "main", title = "Glavni račun"), AccountEntity(uid = "c", title = "Gotovina"))

    private fun tx(cat: String, cents: Long, daysAgo: Long) = TransactionEntity(
        type = TransactionType.EXPENSE, amountCents = cents, date = today.minusDays(daysAgo).millis(), categoryUid = cat,
    )

    @Test fun examplesFollowMostUsedCategories() {
        val txs = List(5) { tx("g", 2340, it.toLong()) } + List(3) { tx("m", 750, it.toLong()) } +
            listOf(tx("b", 5000, 3)) + List(9) { tx("b", 4000, 100) } // stari bencin se ne šteje
        val ex = voiceExamples(txs, categories, accounts, today)
        assertEquals(listOf("Groceries", "Malica", "Bencin"), ex.map { it.categoryTitle })
        // Tuj naziv -> slovenska ključna beseda
        assertEquals("trgovina 23,40", ex[0].phrase)
        assertEquals("malica 7,50 včeraj", ex[1].phrase)
        assertEquals("bencin 50 evrov z gotovino", ex[2].phrase)
        // vsak primer parser res razume
        val parser = VoiceCommandParser(categories, accounts, today)
        ex.zip(listOf("g", "m", "b")).forEach { (e, uid) -> assertEquals(uid, parser.parse(e.phrase).category?.uid) }
        assertTrue("lidl" in ex[0].alternatives)
    }

    @Test fun defaultsWithoutHistory() {
        assertEquals(DEFAULT_VOICE_EXAMPLES, voiceExamples(emptyList(), categories, accounts, today))
    }
}
