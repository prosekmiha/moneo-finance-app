package si.moneo.data.db

import si.moneo.R
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

/** Ob prvem zagonu (prazna baza) napolni privzeti račun in osnovne kategorije. */
suspend fun seedDefaultsIfEmpty(db: AppDatabase) {
    if (db.accountDao().defaultAccount() == null) {
        db.accountDao().upsert(AccountEntity(uid = "main", title = str(R.string.main_account)))
    }
    LEGACY_TITLES.forEach { (old, new) -> db.categoryDao().rename(old, new) }
    if (db.categoryDao().allActive().isNotEmpty()) return

    // Nazivi in ključne besede v jeziku aplikacije ob prvem zagonu
    fun cat(title: Int, keywords: Int?) = str(title) to (keywords?.let { str(it) } ?: "")
    val expense = listOf(
        cat(R.string.cat_food, R.string.cat_food_kw),
        cat(R.string.cat_lunch, R.string.cat_lunch_kw),
        cat(R.string.cat_coffee, R.string.cat_coffee_kw),
        cat(R.string.cat_fuel, R.string.cat_fuel_kw),
        cat(R.string.cat_transport, R.string.cat_transport_kw),
        cat(R.string.cat_home, R.string.cat_home_kw),
        cat(R.string.cat_health, R.string.cat_health_kw),
        cat(R.string.cat_leisure, R.string.cat_leisure_kw),
        cat(R.string.cat_clothes, R.string.cat_clothes_kw),
        cat(R.string.cat_phone, R.string.cat_phone_kw),
        cat(R.string.cat_other, null),
    )
    val income = listOf(
        cat(R.string.cat_salary, R.string.cat_salary_kw),
        cat(R.string.cat_holiday_pay, R.string.cat_holiday_pay_kw),
        cat(R.string.cat_refund, R.string.cat_refund_kw),
        cat(R.string.cat_interest, R.string.cat_interest_kw),
        cat(R.string.cat_gift, R.string.cat_gift_kw),
        cat(R.string.cat_other, null),
    )

    expense.forEachIndexed { i, (title, keywords) ->
        db.categoryDao().upsert(
            CategoryEntity(title = title, type = TransactionType.EXPENSE, keywords = "$title,$keywords".lowercase(), position = i)
        )
    }
    income.forEachIndexed { i, (title, keywords) ->
        db.categoryDao().upsert(
            CategoryEntity(title = title, type = TransactionType.INCOME, keywords = "$title,$keywords".lowercase(), position = i)
        )
    }
}
