package si.moneo

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
import si.moneo.data.db.entity.TransactionType

/**
 * Preveri vse nadgradnje baze (1 -> 7) na pravem SQLite: stara baza s podatki se mora odpreti
 * s trenutno aplikacijo (Room ob tem preveri, da se shema ujema z entitetami), podatki pa ostanejo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before fun clean() { context.deleteDatabase(dbName) }
    @After fun cleanup() { context.deleteDatabase(dbName) }

    /** Ustvari bazo v obliki različice 1 (pred vsemi nadgradnjami) in vanjo zapiše vzorčne podatke. */
    private fun createVersion1() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    V1_SCHEMA.forEach(db::execSQL)
                    db.execSQL("INSERT INTO accounts VALUES ('main','Glavni račun','EUR',NULL,NULL,0,1,1,1,0)")
                    db.execSQL("INSERT INTO categories VALUES ('c1','Hrana','EXPENSE',NULL,NULL,0,'hrana,trgovina',1,1,0)")
                    db.execSQL(
                        "INSERT INTO transactions VALUES ('t1','EXPENSE',2340,'EUR',1727654400000,'Mercator','c1','main','MANUAL',1,NULL,1,1,0)",
                    )
                    db.execSQL("INSERT INTO transfers VALUES ('tr1','main',NULL,5000,5000,'EUR',1727654400000,'',1,1,0)")
                    db.execSQL(
                        "INSERT INTO recurring_rules VALUES ('r1','Najemnina','EXPENSE',50000,NULL,'main','MONTHLY',1,1730419200000,NULL,1,1,1,1,0)",
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()

    @Test fun allMigrationsKeepDataAndMatchSchema() = runBlocking {
        createVersion1()
        val db = openCurrent()
        // Odpiranje sproži nadgradnje 1 -> 7 in Roomovo preverjanje sheme (vrže napako, če se ne ujema)
        db.openHelper.writableDatabase

        val tx = db.transactionDao().all().single()
        assertEquals("t1", tx.uid)
        assertEquals(2340L, tx.amountCents)
        assertEquals("Mercator", tx.comment)
        assertEquals(TransactionType.EXPENSE, tx.type)
        // novi stolpci dobijo privzete vrednosti
        assertEquals("", tx.tags)
        assertNull(tx.subscriptionUid)
        assertNull(tx.attachmentPath)

        val account = db.accountDao().all().single()
        assertEquals(0L, account.initialBalanceCents)
        assertNull(db.categoryDao().all().single().monthlyBudgetCents)
        assertEquals(1, db.transferDao().all().size)
        val rule = db.recurringRuleDao().all().single()
        assertEquals("Najemnina", rule.title)
        assertNull(rule.billingDay)

        // nove tabele obstajajo in delujejo
        assertTrue(db.subscriptionDao().all().isEmpty())
        assertTrue(db.favoriteDao().all().isEmpty())
        assertTrue(db.debtDao().all().isEmpty())
        assertTrue(db.goalDao().allGoals().isEmpty())
        db.close()
    }

    @Test fun freshInstallOpens() = runBlocking {
        val db = openCurrent()
        db.openHelper.writableDatabase
        assertTrue(db.transactionDao().all().isEmpty())
        db.close()
    }

    private companion object {
        /** Shema različice 1 (tako jo je ustvaril Room za tedanje entitete). */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `accounts` (`uid` TEXT NOT NULL, `title` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, " +
                "`icon` TEXT, `color` INTEGER, `position` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
            "CREATE TABLE IF NOT EXISTS `categories` (`uid` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`icon` TEXT, `color` INTEGER, `position` INTEGER NOT NULL, `keywords` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
            "CREATE TABLE IF NOT EXISTS `transactions` (`uid` TEXT NOT NULL, `type` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, " +
                "`currencyCode` TEXT NOT NULL, `date` INTEGER NOT NULL, `comment` TEXT NOT NULL, `categoryUid` TEXT, " +
                "`accountUid` TEXT, `source` TEXT NOT NULL, `confirmed` INTEGER NOT NULL, `recurringRuleUid` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
            "CREATE TABLE IF NOT EXISTS `transfers` (`uid` TEXT NOT NULL, `fromAccountUid` TEXT, `toAccountUid` TEXT, " +
                "`fromAmountCents` INTEGER NOT NULL, `toAmountCents` INTEGER, `currencyCode` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                "`comment` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "PRIMARY KEY(`uid`))",
            "CREATE TABLE IF NOT EXISTS `recurring_rules` (`uid` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, `categoryUid` TEXT, `accountUid` TEXT, `frequency` TEXT NOT NULL, " +
                "`interval` INTEGER NOT NULL, `nextDueDate` INTEGER NOT NULL, `endDate` INTEGER, `autoAdd` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "PRIMARY KEY(`uid`))",
        )
    }
}
