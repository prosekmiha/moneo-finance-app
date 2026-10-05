package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.domain.receipt.OcrLine
import si.moneo.domain.receipt.ReceiptParser
import java.time.LocalDate

class ReceiptParserTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test fun cashTenderedIsNotTotal() {
        val rows = listOf(
            "MERCATOR d.d.",
            "Dunajska cesta 107, Ljubljana",
            "Račun št. 123-45",
            "Kruh 2,10",
            "Mleko 1,29",
            "SKUPAJ 23,40",
            "GOTOVINA 50,00",
            "VRAČILO 26,60",
            "DDV 9,5% 2,03",
            "30.09.2026 14:32",
        )
        val r = ReceiptParser.parse(rows, today)
        assertEquals(2340L, r.totalCents)
        assertEquals(LocalDate.of(2026, 9, 30), r.date)
        assertEquals("Mercator", r.merchant)
        assertTrue(5000L in r.candidates)
    }

    @Test fun labelAndAmountInSeparateColumnsAreJoined() {
        // ML Kit: levi stolpec in desni stolpec kot ločeni vrstici na isti višini
        val lines = listOf(
            OcrLine("HOFER", 100, 10, 300, 50),
            OcrLine("SKUPAJ ZA PLAČILO", 20, 400, 300, 430),
            OcrLine("EUR 18,75", 500, 402, 640, 432),
            OcrLine("Plačano s kartico", 20, 450, 300, 480),
            OcrLine("18,75", 500, 452, 600, 482),
        )
        val rows = ReceiptParser.rows(lines)
        assertEquals("SKUPAJ ZA PLAČILO EUR 18,75", rows[1])
        assertEquals(1875L, ReceiptParser.parse(rows, today).totalCents)
    }

    @Test fun amountOnNextRowAfterLabel() {
        val r = ReceiptParser.parse(listOf("Trgovina Jager", "ZA PLAČILO", "12,99", "GOTOVINA 20,00"), today)
        assertEquals(1299L, r.totalCents)
    }

    @Test fun thousandsSeparators() {
        assertEquals(listOf(123456L), ReceiptParser.amountsIn("SKUPAJ 1.234,56 EUR"))
        assertEquals(listOf(123456L), ReceiptParser.amountsIn("Total 1,234.56"))
        assertEquals(listOf(123456L), ReceiptParser.amountsIn("Skupaj 1 234,56"))
        // datum ni znesek
        assertTrue(ReceiptParser.amountsIn("Datum 30.09.2026").isEmpty())
    }

    @Test fun implausibleDatesIgnored() {
        assertNull(ReceiptParser.findDate(listOf("Veljavno do 31.12.2027"), today))
        assertEquals(LocalDate.of(2026, 9, 28), ReceiptParser.findDate(listOf("28/09/26 18:01"), today))
    }

    @Test fun fallbackLargestNonExcluded() {
        val r = ReceiptParser.parse(listOf("Kava 1,80", "Rogljiček 1,20", "Gotovina 5,00"), today)
        assertEquals(180L, r.totalCents)
    }
}
