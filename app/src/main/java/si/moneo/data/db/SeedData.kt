package si.moneo.data.db

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import si.moneo.R
import si.moneo.ui.AppLocale
import si.moneo.ui.str
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionType

/**
 * Stari privzeti nazivi brez šumnikov -> pravilni. Ob vsakem zagonu popravi obstoječe
 * namestitve (idempotentno). Ključne besede se primerjajo brez šumnikov, zato jih ni treba spreminjati.
 */
private val LEGACY_TITLES = mapOf(
    "Prosti cas" to "Prosti čas",
    "Oblacila" to "Oblačila",
    "Placa" to "Plača",
    "Vracilo" to "Vračilo",
)

/** Privzeta kategorija: naziv in ključne besede (viri), da jih lahko prevedemo ob menjavi jezika. */
private class DefaultCategory(val type: TransactionType, val title: Int, val keywords: Int?)

private val DEFAULT_EXPENSES = listOf(
    R.string.cat_food to R.string.cat_food_kw,
    R.string.cat_lunch to R.string.cat_lunch_kw,
    R.string.cat_coffee to R.string.cat_coffee_kw,
    R.string.cat_fuel to R.string.cat_fuel_kw,
    R.string.cat_transport to R.string.cat_transport_kw,
    R.string.cat_home to R.string.cat_home_kw,
    R.string.cat_health to R.string.cat_health_kw,
    R.string.cat_leisure to R.string.cat_leisure_kw,
    R.string.cat_clothes to R.string.cat_clothes_kw,
    R.string.cat_phone to R.string.cat_phone_kw,
    R.string.cat_other to null,
).map { (t, k) -> DefaultCategory(TransactionType.EXPENSE, t, k) }

private val DEFAULT_INCOMES = listOf(
    R.string.cat_salary to R.string.cat_salary_kw,
    R.string.cat_holiday_pay to R.string.cat_holiday_pay_kw,
    R.string.cat_refund to R.string.cat_refund_kw,
    R.string.cat_interest to R.string.cat_interest_kw,
    R.string.cat_gift to R.string.cat_gift_kw,
    R.string.cat_other to null,
).map { (t, k) -> DefaultCategory(TransactionType.INCOME, t, k) }

/** Vse kategorije, ki jih aplikacija ustvari sama (tudi "Naročnine" ob prvi naročnini). */
private val TRANSLATABLE_CATEGORIES = DEFAULT_EXPENSES + DEFAULT_INCOMES +
    DefaultCategory(TransactionType.EXPENSE, R.string.cat_subscriptions, R.string.cat_subscriptions_kw)

const val MAIN_ACCOUNT_UID = "main"

private fun keywordSet(text: String) = text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

/**
 * Glavni račun in privzete kategorije dobijo nazive v jeziku prvega zagona. Ob menjavi jezika jih
 * prevedemo, a le tiste, ki jih uporabnik ni preimenoval (naziv je še privzeti naziv v katerem od
 * podprtih jezikov). [context] mora biti v jeziku aplikacije (aktivnost - na starejšem Androidu
 * kontekst aplikacije ni preveden). Vrne false, če privzeti podatki še niso ustvarjeni (prvi zagon).
 */
suspend fun localizeDefaults(db: AppDatabase, context: Context): Boolean {
    // Viri v vseh podprtih jezikih (le enkrat na klic)
    val languages by lazy {
        AppLocale.SUPPORTED.map { tag ->
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
            )
        }
    }
    val now = System.currentTimeMillis()

    val main = db.accountDao().byUid(MAIN_ACCOUNT_UID)
    if (main != null) {
        val current = context.getString(R.string.main_account)
        val known = main.title == "Glavni racun" || languages.any { it.getString(R.string.main_account) == main.title }
        // Če že obstaja drug račun s tem nazivom, ga ne podvajamo
        val taken = db.accountDao().all().any { it.uid != main.uid && !it.deleted && it.title.equals(current, ignoreCase = true) }
        if (main.title != current && known && !taken) db.accountDao().upsert(main.copy(title = current, updatedAt = now))
    }

    val categories = db.categoryDao().allActive()
    val titles = categories.groupBy({ it.type }, { it.title.lowercase() }).mapValues { it.value.toMutableSet() }
    for (category in categories) {
        // Starejši slovenski nazivi brez šumnikov
        val title = LEGACY_TITLES[category.title] ?: category.title
        for (default in TRANSLATABLE_CATEGORIES.filter { it.type == category.type }) {
            val newTitle = context.getString(default.title)
            if (category.title == newTitle) break
            val matching = languages.filter { it.getString(default.title) == title }
            if (matching.isEmpty()) continue
            val typeTitles = titles.getValue(category.type)
            if (newTitle.lowercase() in typeTitles) break // kategorija s tem nazivom že obstaja
            val newKeywords = default.keywords?.let { context.getString(it) }.orEmpty()
            // Nespremenjene ključne besede zamenjamo s prevodom, uporabnikove dopolnimo
            val current = keywordSet(category.keywords) - title.lowercase()
            val untouched = matching.any { lang ->
                current == keywordSet(default.keywords?.let { lang.getString(it) }.orEmpty()) - title.lowercase()
            }
            val words = (listOf(newTitle) + newKeywords.split(',') + if (untouched) emptyList() else category.keywords.split(','))
                .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            db.categoryDao().upsert(category.copy(title = newTitle, keywords = words.joinToString(","), updatedAt = now))
            typeTitles -= category.title.lowercase()
            typeTitles += newTitle.lowercase()
            break
        }
    }
    return main != null || categories.isNotEmpty()
}

/** Ob prvem zagonu (prazna baza) napolni privzeti račun in osnovne kategorije. */
suspend fun seedDefaultsIfEmpty(db: AppDatabase) {
    if (db.accountDao().defaultAccount() == null) {
        db.accountDao().upsert(AccountEntity(uid = MAIN_ACCOUNT_UID, title = str(R.string.main_account), isDefault = true))
    }
    LEGACY_TITLES.forEach { (old, new) -> db.categoryDao().rename(old, new) }
    if (db.categoryDao().allActive().isNotEmpty()) return

    // Nazivi in ključne besede v jeziku aplikacije ob prvem zagonu
    (DEFAULT_EXPENSES + DEFAULT_INCOMES).groupBy { it.type }.forEach { (type, defaults) ->
        defaults.forEachIndexed { i, default ->
            val title = str(default.title)
            val keywords = default.keywords?.let { str(it) }.orEmpty()
            db.categoryDao().upsert(CategoryEntity(title = title, type = type, keywords = "$title,$keywords".lowercase(), position = i))
        }
    }
}
