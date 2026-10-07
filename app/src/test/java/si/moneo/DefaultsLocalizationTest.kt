package si.moneo

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.moneo.data.db.AppDatabase
import si.moneo.data.db.MAIN_ACCOUNT_UID
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.localizeDefaults
import java.util.Locale

/** Glavni račun in privzete kategorije sledijo jeziku aplikacije, dokler jih uporabnik ne preimenuje. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DefaultsLocalizationTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    private fun inLanguage(tag: String): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    private suspend fun mainTitle() = db.accountDao().byUid(MAIN_ACCOUNT_UID)!!.title

    @Test fun defaultNameFollowsLanguage() = runBlocking {
        db.accountDao().upsert(AccountEntity(uid = MAIN_ACCOUNT_UID, title = "Main account", isDefault = true))
        localizeDefaults(db, inLanguage("sl"))
        assertEquals("Glavni račun", mainTitle())
        localizeDefaults(db, inLanguage("de"))
        assertEquals("Hauptkonto", mainTitle())
        localizeDefaults(db, inLanguage("en"))
        assertEquals("Main account", mainTitle())
    }

    @Test fun renamedAccountStays() = runBlocking {
        db.accountDao().upsert(AccountEntity(uid = MAIN_ACCOUNT_UID, title = "NLB", isDefault = true))
        localizeDefaults(db, inLanguage("sl"))
        assertEquals("NLB", mainTitle())
    }

    private suspend fun category(title: String) = db.categoryDao().all().single { it.title == title }

    @Test fun defaultCategoriesFollowLanguage() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(title = "Food", type = TransactionType.EXPENSE, keywords = "food,groceries,supermarket,shop"))
        db.categoryDao().upsert(CategoryEntity(title = "Subscriptions", type = TransactionType.EXPENSE, keywords = "subscriptions,netflix,mojflix"))
        db.categoryDao().upsert(CategoryEntity(title = "Hobiji", type = TransactionType.EXPENSE, keywords = "hobiji"))
        localizeDefaults(db, inLanguage("sl"))

        // nespremenjene ključne besede se zamenjajo s slovenskimi
        assertEquals(setOf("hrana", "trgovina", "mercator", "hofer", "lidl", "spar", "tuš"), category("Hrana").keywords.split(',').toSet())
        // uporabnikove ključne besede ostanejo, dodajo se slovenske
        val subs = category("Naročnine").keywords.split(',')
        assertTrue("mojflix" in subs && "naročnina" in subs)
        // lastna kategorija ostane nespremenjena
        assertEquals("hobiji", category("Hobiji").keywords)
    }

    @Test fun noDuplicateCategory() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(title = "Food", type = TransactionType.EXPENSE, keywords = "food"))
        db.categoryDao().upsert(CategoryEntity(title = "Hrana", type = TransactionType.EXPENSE, keywords = "hrana"))
        localizeDefaults(db, inLanguage("sl"))
        assertEquals(listOf("Food", "Hrana"), db.categoryDao().all().map { it.title }.sorted())
    }
}
