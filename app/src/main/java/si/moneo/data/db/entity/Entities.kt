package si.moneo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Vse entitete so od začetka "sync-ready":
 *  - uid (UUID) kot primarni ključ -> ni konfliktov med napravami
 *  - createdAt / updatedAt (epoch millis) -> "last write wins" združevanje
 *  - deleted (soft delete) -> brisi se propagirajo ob syncu
 */

enum class TransactionType { EXPENSE, INCOME }

enum class TransactionSource { MANUAL, VOICE, NOTIFICATION, OCR, RECURRING, IMPORT, SUBSCRIPTION }

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** LENT = posodil sem (dolgujejo meni), BORROWED = izposodil sem si (jaz dolgujem). */
enum class DebtDirection { LENT, BORROWED }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val currencyCode: String = "EUR",
    val icon: String? = null,
    val color: Int? = null,
    val position: Int = 0,
    val isActive: Boolean = true,
    /** Stanje računa ob začetku vodenja (ni prihodek - ne vpliva na statistiko). */
    @ColumnInfo(defaultValue = "0") val initialBalanceCents: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** Račun za transakcije, pravila in naročnine brez izbranega računa. */
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
)

/** Privzeti račun med aktivnimi; če ni označen, prvi po vrstnem redu (kot [si.moneo.data.db.AccountDao.defaultAccount]). */
fun List<AccountEntity>.defaultAccount(): AccountEntity? = firstOrNull { it.isDefault } ?: firstOrNull()

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val type: TransactionType,
    val icon: String? = null,
    val color: Int? = null,
    val position: Int = 0,
    /** Ključne besede za samodejno kategorizacijo (glasovni vnos, obvestila, OCR), ločene z vejico. */
    val keywords: String = "",
    /** Mesečni proračun v centih (null = brez proračuna). */
    val monthlyBudgetCents: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val type: TransactionType,
    /** Znesek v centih (npr. 6,10 EUR -> 610). */
    val amountCents: Long,
    val currencyCode: String = "EUR",
    /** Epoch millis lokalnega datuma (ob polnoči). */
    val date: Long,
    val comment: String = "",
    val categoryUid: String? = null,
    val accountUid: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    /** false = predlog (npr. iz obvestila/OCR), ki ga uporabnik še mora potrditi. */
    val confirmed: Boolean = true,
    /** uid ponavljajočega pravila, ki je ustvarilo transakcijo (če obstaja). */
    val recurringRuleUid: String? = null,
    /** uid naročnine, ki je ustvarila transakcijo (če obstaja). */
    val subscriptionUid: String? = null,
    /** Oznake, ločene z vejico (npr. "Dopust Hrvaška 2026"); prečkajo kategorije. */
    val tags: String = "",
    /** Pot do priložene slike (npr. fotografija računa) v notranjem pomnilniku aplikacije. */
    val attachmentPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val fromAccountUid: String? = null,
    val toAccountUid: String? = null,
    val fromAmountCents: Long,
    val toAmountCents: Long? = null,
    val currencyCode: String = "EUR",
    val date: Long,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val type: TransactionType,
    val amountCents: Long,
    val categoryUid: String? = null,
    val accountUid: String? = null,
    val frequency: RecurrenceFrequency,
    /** Interval (npr. vsaka 2 tedna = frequency WEEKLY, interval 2). */
    val interval: Int = 1,
    /** Naslednji zapadli datum (epoch millis). */
    val nextDueDate: Long,
    /** Dan v mesecu za mesečna/letna pravila (ohrani 31., ko ima vmesni mesec manj dni); null = stara pravila. */
    val billingDay: Int? = null,
    val endDate: Long? = null,
    /** true = zapiše kot potrjeno, false = zapiše kot nepotrjeno (potrdi uporabnik) + opomnik. */
    val autoAdd: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/**
 * Naročnina (Netflix, Spotify, telefon ...). Na dan plačila se samodejno zapiše kot odhodek
 * (glej FinanceRepository.chargeDueSubscriptions), nato se [nextPaymentDate] premakne naprej.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val amountCents: Long,
    val currencyCode: String = "EUR",
    /** Obračunsko obdobje: npr. MONTHLY z intervalom 3 = četrtletno. */
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val interval: Int = 1,
    /** Datum naslednjega plačila (epoch millis lokalnega datuma ob polnoči). */
    val nextPaymentDate: Long,
    /** Dan v mesecu, na katerega se plačuje (ohrani 31., ko ima vmesni mesec manj dni). */
    val billingDay: Int,
    /** Začetek naročnine (samo informativno). */
    val startDate: Long? = null,
    /** Po tem datumu se ne zaračunava več (odpovedana naročnina). */
    val endDate: Long? = null,
    val categoryUid: String? = null,
    val accountUid: String? = null,
    /** Opomnik N dni pred plačilom (0 = na dan plačila, null = brez opomnika). */
    val remindDaysBefore: Int? = null,
    /** Spletna stran za upravljanje/odpoved naročnine. */
    val url: String = "",
    val note: String = "",
    /** false = začasno ustavljena (se ne zaračunava). */
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** Priljubljen vnos: shranjen strošek/prihodek, ki se doda z enim tapom (v aplikaciji ali widgetu). */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    /** Kratek naziv na gumbu (npr. "Kava"). */
    val title: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountCents: Long,
    val categoryUid: String? = null,
    val accountUid: String? = null,
    /** Opomba, ki se zapiše k transakciji (prazno = naziv, če ni kategorije). */
    val comment: String = "",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** Dolg ali posojilo med uporabnikom in drugo osebo (ne vpliva na stanje računov). */
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val person: String,
    val direction: DebtDirection,
    val amountCents: Long,
    /** Koliko je že vrnjenega; dolg je poravnan, ko je enako [amountCents]. */
    val repaidCents: Long = 0,
    /** Datum posojila (epoch millis). */
    val date: Long,
    /** Rok vračila (epoch millis), neobvezen - na ta dan pride opomnik. */
    val dueDate: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val remainingCents: Long get() = (amountCents - repaidCents).coerceAtLeast(0)
    val settled: Boolean get() = repaidCents >= amountCents
}

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val emoji: String = "\uD83C\uDFAF",
    val targetCents: Long,
    /** Rok (epoch millis), neobvezen. */
    val deadline: Long? = null,
    val color: Int? = null,
    /** Samodejno mesečno vplačilo v centih (null = izklopljeno). */
    val monthlyAutoCents: Long? = null,
    /** Dan v mesecu za samodejno vplačilo (1-28). */
    val autoDay: Int = 1,
    /** Zadnji mesec (yyyy-MM), za katerega je bilo samodejno vplačilo že zapisano. */
    val lastAutoMonth: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "goal_contributions")
data class GoalContributionEntity(
    @PrimaryKey val uid: String = UUID.randomUUID().toString(),
    val goalUid: String,
    /** Pozitivno = vplačilo, negativno = dvig. */
    val amountCents: Long,
    val date: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
