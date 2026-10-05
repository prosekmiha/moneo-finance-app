package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.domain.text.WordMatch
import si.moneo.domain.voice.VoiceCommandParser
import si.moneo.domain.voice.VoiceVocabulary
import java.time.LocalDate

class VoiceParserTest {

    // Sreda, 30. 9. 2026
    private val today = LocalDate.of(2026, 9, 30)
    private val categories = listOf(
        CategoryEntity(uid = "g", title = "Groceries", type = TransactionType.EXPENSE, keywords = "trgovina,mercator,lidl"),
        CategoryEntity(uid = "b", title = "Bencin", type = TransactionType.EXPENSE, keywords = "bencin,petrol"),
        CategoryEntity(uid = "p", title = "Plača", type = TransactionType.INCOME, keywords = "plača"),
    )
    private val accounts = listOf(
        AccountEntity(uid = "main", title = "Glavni račun"),
        AccountEntity(uid = "cash", title = "Gotovina"),
        AccountEntity(uid = "rev", title = "Revolut"),
    )
    private fun parse(text: String) = VoiceCommandParser(categories, accounts, today).parse(text)

    @Test fun compoundNumberWords() {
        assertEquals(2400L, parse("štiriindvajset evrov").amountCents)
        assertEquals(35000L, parse("tristo petdeset").amountCents)
        assertEquals(250000L, parse("dva tisoč petsto").amountCents)
        assertEquals(1250L, parse("dvanajst evrov petdeset").amountCents)
        assertEquals(250L, parse("dve cela pet").amountCents)
        assertEquals(50L, parse("petdeset centov").amountCents)
    }

    @Test fun digitForms() {
        assertEquals(450L, parse("malica 4,50").amountCents)
        assertEquals(1230L, parse("12 evrov 30").amountCents)
        assertEquals(250L, parse("kava 2,50.").amountCents)
    }

    @Test fun wordFormsMatchKeywords() {
        val p = parse("v trgovini 23,40")
        assertEquals("g", p.category?.uid)
        assertEquals(2340L, p.amountCents)
        assertEquals("g", parse("lidlu 15 evrov").category?.uid)
        assertEquals("b", parse("na petrolu 40").category?.uid)
    }

    @Test fun weekdaysAndDates() {
        assertEquals(LocalDate.of(2026, 9, 25), parse("kava 3 v petek").date)
        assertEquals(today, parse("kava 3 v sredo").date)
        assertEquals(LocalDate.of(2026, 9, 27), parse("kava 3 pred tremi dnevi").date)
        val d = parse("bencin 50 15. septembra")
        assertEquals(LocalDate.of(2026, 9, 15), d.date)
        assertEquals(5000L, d.amountCents)
        // prihodnji datum brez leta -> lansko leto
        assertEquals(LocalDate.of(2025, 12, 24), parse("darilo 30 24. decembra").date)
        assertEquals(LocalDate.of(2026, 9, 12), parse("kosilo 12 12. 9.").date)
    }

    @Test fun accountFromSpeech() {
        assertEquals("cash", parse("kava 2 z gotovino").account?.uid)
        assertEquals("rev", parse("kino 9 z revolutom").account?.uid)
        assertNull(parse("kava 2 na račun").account)
    }

    @Test fun incomeFromCategory() {
        val p = parse("plača tisoč dvesto")
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals(120000L, p.amountCents)
    }

    @Test fun commentKeepsUnknownWords() {
        // "kava" ni kategorija v tem testu, zato ostane v opombi
        assertEquals("kava pri toniju", parse("kava 2,50 pri toniju").comment)
        assertEquals("pri toniju", parse("mercator 2,50 pri toniju").comment)
    }

    @Test fun sameWordRules() {
        assertTrue(WordMatch.sameWord("trgovina", "trgovini"))
        assertTrue(WordMatch.sameWord("hofer", "hoferju"))
        assertTrue(WordMatch.sameWord("kava", "kavo"))
        assertFalse(WordMatch.sameWord("pet", "petrol"))
        assertFalse(WordMatch.sameWord("stiri", "stirinajst"))
        assertFalse(WordMatch.sameWord("kino", "kosilo"))
    }

    @Test fun vocabularyOfAppLanguage() {
        // Besede v jeziku aplikacije (danščina: "i går" sta dve besedi), šumniki/naglasi niso pomembni
        val vocab = VoiceVocabulary.of("indtægt,indtægter", "udgift", "i dag", "i går")
        val kaffe = CategoryEntity(uid = "k", title = "Kaffe", type = TransactionType.EXPENSE)
        val salary = CategoryEntity(uid = "l", title = "Løn", type = TransactionType.EXPENSE)
        val parser = VoiceCommandParser(listOf(kaffe, salary), emptyList(), today, vocab)
        val p = parser.parse("kaffe 2,50 i går")
        assertEquals(250L, p.amountCents)
        assertEquals("k", p.category?.uid)
        assertEquals(today.minusDays(1), p.date)
        val income = parser.parse("indtægt løn 1200")
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals("l", income.category?.uid)
        assertEquals(120000L, income.amountCents)
    }

    @Test fun foldStripsDiacriticsOfAllLanguages() {
        assertEquals("cafe", WordMatch.fold("Café"))
        assertEquals("strasse", WordMatch.fold("Straße"))
        assertEquals("lon", WordMatch.fold("Løn"))
        assertEquals("sumniki cszcd", WordMatch.fold("Šumniki čšžćđ"))
    }
}
