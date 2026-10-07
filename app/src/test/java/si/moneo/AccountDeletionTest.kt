package si.moneo

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.moneo.data.db.AppDatabase
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity
import si.moneo.data.repo.FinanceRepository

/** Brisanje računa: prenos na drug račun ohrani skupno stanje, brisanje ne pusti "visečih" povezav, oboje se da razveljaviti. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountDeletionTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = FinanceRepository(db)

    @Before fun seed() = runBlocking {
        db.accountDao().upsertAll(
            listOf(
                AccountEntity(uid = "main", title = "Glavni račun", initialBalanceCents = 10_000, isDefault = true),
                AccountEntity(uid = "cash", title = "Gotovina", initialBalanceCents = 5_000, position = 1),
            ),
        )
        db.transactionDao().upsert(TransactionEntity(uid = "t1", type = TransactionType.EXPENSE, amountCents = 2_000, date = 0, accountUid = "cash"))
        db.transferDao().upsert(TransferEntity(uid = "tr1", fromAccountUid = "main", toAccountUid = "cash", fromAmountCents = 3_000, date = 0))
        db.recurringRuleDao().upsert(
            RecurringRuleEntity(
                uid = "r1", title = "Najemnina", type = TransactionType.EXPENSE, amountCents = 50_000,
                accountUid = "cash", frequency = RecurrenceFrequency.MONTHLY, nextDueDate = 0,
            ),
        )
    }

    @After fun close() = db.close()

    /** Skupno stanje vseh aktivnih računov (začetno stanje + transakcije + prenosi). */
    private suspend fun total(): Long {
        val active = db.accountDao().all().filter { !it.deleted }.map { it.uid }.toSet()
        val txs = db.transactionDao().all().filter { !it.deleted && it.accountUid in active }
            .sumOf { if (it.type == TransactionType.INCOME) it.amountCents else -it.amountCents }
        val transfers = db.transferDao().all().filter { !it.deleted }.sumOf { t ->
            (if (t.toAccountUid in active) t.toAmountCents ?: t.fromAmountCents else 0) -
                (if (t.fromAccountUid in active) t.fromAmountCents else 0)
        }
        return db.accountDao().all().filter { !it.deleted }.sumOf { it.initialBalanceCents } + txs + transfers
    }

    @Test fun usageCountsTransactionsAndTransfers() = runBlocking {
        assertEquals(2, repo.accountUsage("cash"))
    }

    @Test fun moveKeepsTotalBalance() = runBlocking {
        val before = total()
        repo.deleteAccount("cash", moveTo = "main")

        assertEquals(before, total())
        assertTrue(db.accountDao().byUid("cash")!!.deleted)
        assertEquals("main", db.transactionDao().byUid("t1")!!.accountUid)
        assertEquals("main", db.recurringRuleDao().all().single().accountUid)
        // prenos med združenima računoma nima več pomena
        assertTrue(db.transferDao().all().single().deleted)
        assertEquals(15_000L, db.accountDao().byUid("main")!!.initialBalanceCents)
    }

    @Test fun dropDeletesTransactionsAndFreesRules() = runBlocking {
        repo.deleteAccount("cash", moveTo = null)

        assertTrue(db.transactionDao().byUid("t1")!!.deleted)
        // pravilo gre na privzeti račun namesto na izbrisanega
        assertNull(db.recurringRuleDao().all().single().accountUid)
        // prenos je del stanja glavnega računa, zato ostane
        assertTrue(!db.transferDao().all().single().deleted)
    }

    @Test fun undoRestoresEverything() = runBlocking {
        val before = total()
        val removal = repo.deleteAccount("cash", moveTo = "main")
        repo.restoreAccount(removal)

        assertEquals(before, total())
        assertTrue(!db.accountDao().byUid("cash")!!.deleted)
        assertEquals("cash", db.transactionDao().byUid("t1")!!.accountUid)
        assertEquals("cash", db.recurringRuleDao().all().single().accountUid)
        assertTrue(!db.transferDao().all().single().deleted)
        assertEquals(10_000L, db.accountDao().byUid("main")!!.initialBalanceCents)
    }
}
