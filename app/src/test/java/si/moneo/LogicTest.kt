package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity
import si.moneo.domain.notifications.BankNotificationParser
import si.moneo.ui.TransactionUi
import si.moneo.ui.accountBalances
import si.moneo.ui.components.applyKey
import si.moneo.ui.detectSubscriptions
import si.moneo.ui.evaluateAmountExpression
import si.moneo.ui.str
import java.time.LocalDate

class BankNotificationParserTest {

    @Test fun slovenianCardPayment() {
        val r = BankNotificationParser.parse("NLB Klik", "Plačilo s kartico 12,34 EUR pri SPAR LJUBLJANA")!!
        assertEquals(1234, r.amountCents)
        assertFalse(r.isIncome)
        assertTrue(r.merchant.startsWith("SPAR"))
    }

    @Test fun thousandsSeparatorIncome() {
        val r = BankNotificationParser.parse("Banka", "Priliv na račun: 1.234,56 EUR od DELODAJALEC D.O.O.")!!
        assertEquals(123456, r.amountCents)
        assertTrue(r.isIncome)
    }

    @Test fun revolutEnglishDotDecimal() {
        val r = BankNotificationParser.parse("Revolut", "You paid €12.34 at Starbucks")!!
        assertEquals(1234, r.amountCents) // stari parser je to prebral kot 1.234,00 €
        assertFalse(r.isIncome)
        assertEquals("Starbucks", r.merchant)
    }

    @Test fun receivedMoney() {
        val r = BankNotificationParser.parse("Revolut", "You received €50 from Ana")!!
        assertEquals(5000, r.amountCents)
        assertTrue(r.isIncome)
    }

    @Test fun ignoresUnrelatedNotifications() {
        assertNull(BankNotificationParser.parse("Trgovina", "Nova akcija: 20 % popust na vse!"))
        assertNull(BankNotificationParser.parse("Sporočilo", "Se vidiva ob 12.30?"))
        // "pos" mora biti cela beseda, ne začetek "poslali"
        assertNull(BankNotificationParser.parse("Pošta", "Poslali smo vam paket, 5 EUR poštnine"))
    }

    @Test fun parseAmountFormats() {
        assertEquals(123456L, BankNotificationParser.parseAmount("1.234,56"))
        assertEquals(123456L, BankNotificationParser.parseAmount("1,234.56"))
        assertEquals(1250L, BankNotificationParser.parseAmount("12,5"))
        assertEquals(123400L, BankNotificationParser.parseAmount("1.234"))
        assertEquals(1200L, BankNotificationParser.parseAmount("12"))
    }
}

class CalculatorTest {

    @Test fun addition() = assertEquals(670L, evaluateAmountExpression("4,50+2,20"))

    @Test fun subtraction() = assertEquals(250L, evaluateAmountExpression("4,50−2"))

    @Test fun keysLimitDecimals() {
        var e = ""
        listOf("1", ",", "2", "3", "4").forEach { e = applyKey(e, it) }
        assertEquals("1,23", e)
    }

    @Test fun equalsCollapsesExpression() {
        var e = ""
        listOf("4", ",", "5", "+", "2", ",", "2", "=").forEach { e = applyKey(e, it) }
        assertEquals("6,70", e)
    }
}

class AnalyticsTest {

    private fun tx(
        comment: String, cents: Long, date: LocalDate,
        type: TransactionType = TransactionType.EXPENSE, account: String? = "a",
    ) = TransactionUi(
        uid = "$comment-$date", type = type, amountCents = cents, date = date, comment = comment,
        categoryUid = null, categoryTitle = null, categoryColor = null, accountUid = account, accountTitle = null,
        source = TransactionSource.MANUAL, confirmed = true,
    )

    private val today = LocalDate.of(2026, 9, 29)

    @Test fun detectsMonthlySubscription() {
        val txs = (1..4L).map { tx("Netflix", 1299, today.minusMonths(it).withDayOfMonth(5)) } +
            listOf(tx("kava", 250, today), tx("kava", 260, today.minusDays(1)))
        val s = detectSubscriptions(txs, emptyList(), today)
        assertEquals(1, s.size)
        assertEquals("Netflix", s[0].title)
        assertEquals(1299L, s[0].amountCents)
    }

    @Test fun skipsFrequentAndCovered() {
        // kava vsak dan - ni mesečna naročnina
        val daily = (0..90L).map { tx("kava", 250, today.minusDays(it)) }
        assertTrue(detectSubscriptions(daily, emptyList(), today).isEmpty())
        // že obstaja pravilo z istim imenom
        val netflix = (1..4L).map { tx("Netflix", 1299, today.minusMonths(it)) }
        val rule = RecurringRuleEntity(
            title = "netflix", type = TransactionType.EXPENSE, amountCents = 1299,
            frequency = RecurrenceFrequency.MONTHLY, nextDueDate = 0,
        )
        assertTrue(detectSubscriptions(netflix, listOf(rule), today).isEmpty())
    }

    @Test fun dailyAverageCurrentMonthUsesElapsedDays() {
        val start = LocalDate.of(2026, 9, 1)
        val txs = listOf(
            tx("plača", 2900_00, LocalDate.of(2026, 9, 5), TransactionType.INCOME),
            tx("hrana", 290_00, LocalDate.of(2026, 9, 10)),
            tx("lani", 999_00, LocalDate.of(2026, 8, 31)), // izven obdobja
        )
        // 29. 9. = 29 pretečenih dni
        val avg = si.moneo.ui.dailyAverage(txs, si.moneo.ui.StatsPeriod.MONTH, start, start.plusMonths(1), today)
        assertEquals(29L, avg.days)
        assertEquals(100_00L, avg.incomeCents)
        assertEquals(10_00L, avg.expenseCents)
        assertEquals(str(R.string.avg_to_date), avg.note)
    }

    @Test fun dailyAveragePastPeriodUsesFullLength() {
        val start = LocalDate.of(2026, 8, 1)
        val txs = listOf(tx("hrana", 31_00, LocalDate.of(2026, 8, 15)))
        val avg = si.moneo.ui.dailyAverage(txs, si.moneo.ui.StatsPeriod.MONTH, start, start.plusMonths(1), today)
        assertEquals(31L, avg.days)
        assertEquals(1_00L, avg.expenseCents)
        assertEquals("", avg.note)
    }

    @Test fun dailyAverageForDayUsesLast30Days() {
        val txs = listOf(tx("hrana", 300_00, today.minusDays(10)), tx("staro", 999_00, today.minusDays(40)))
        val avg = si.moneo.ui.dailyAverage(txs, si.moneo.ui.StatsPeriod.DAY, today, today.plusDays(1), today)
        assertEquals(30L, avg.days)
        assertEquals(10_00L, avg.expenseCents)
    }

    @Test fun balanceIncludesInitialAndTransfers() {
        val a = AccountEntity(uid = "a", title = "Kartica", initialBalanceCents = 100_00)
        val b = AccountEntity(uid = "b", title = "Gotovina")
        val txs = listOf(tx("plača", 1000_00, today, TransactionType.INCOME), tx("kosilo", 12_00, today))
        val transfers = listOf(TransferEntity(fromAccountUid = "a", toAccountUid = "b", fromAmountCents = 50_00, date = 0))
        val bal = accountBalances(listOf(a, b), txs, transfers).associate { it.account.uid to it.balanceCents }
        assertEquals(100_00L + 1000_00 - 12_00 - 50_00, bal["a"])
        assertEquals(50_00L, bal["b"])
    }
}
