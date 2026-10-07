package si.moneo.data.repo

import si.moneo.R
import si.moneo.ui.str
import si.moneo.ui.L10n
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import si.moneo.data.db.AppDatabase
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import si.moneo.domain.subscriptions.effectiveBillingDay
import si.moneo.domain.subscriptions.nextAfter
import si.moneo.domain.subscriptions.nextPaymentAfter
import si.moneo.domain.subscriptions.occurrencesUntil
import si.moneo.domain.goals.autoSavingPlan
import si.moneo.domain.text.WordMatch
import si.moneo.domain.subscriptions.paymentsUntil

class FinanceRepository(private val db: AppDatabase) {

    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val transfers = db.transferDao()
    private val recurring = db.recurringRuleDao()
    private val goals = db.goalDao()
    private val subscriptions = db.subscriptionDao()
    private val favorites = db.favoriteDao()
    private val debts = db.debtDao()
    /** Worker in zagon aplikacije lahko hkrati knjižita naročnine - naj ne podvajata. */
    private val chargeLock = Mutex()
    /** Hkratno shranjevanje dveh naročnin ne sme ustvariti dveh kategorij "Naročnine". */
    private val categoryLock = Mutex()

    val allTransactions: Flow<List<TransactionEntity>> = transactions.observeAll()
    val unconfirmedTransactions: Flow<List<TransactionEntity>> = transactions.observeUnconfirmed()
    val allAccounts: Flow<List<AccountEntity>> = accounts.observeActive()
    val allCategories: Flow<List<CategoryEntity>> = categories.observeActive()
    val allTransfers: Flow<List<TransferEntity>> = transfers.observeAll()
    val activeRecurringRules: Flow<List<RecurringRuleEntity>> = recurring.observeActive()
    val allGoals: Flow<List<SavingsGoalEntity>> = goals.observeGoals()
    val allGoalContributions: Flow<List<GoalContributionEntity>> = goals.observeContributions()
    val allSubscriptions: Flow<List<SubscriptionEntity>> = subscriptions.observeActive()
    val allFavorites: Flow<List<FavoriteEntity>> = favorites.observeActive()
    val allDebts: Flow<List<DebtEntity>> = debts.observeActive()

    suspend fun saveTransaction(tx: TransactionEntity) {
        val withAccount = if (tx.accountUid == null) {
            tx.copy(accountUid = accounts.defaultAccount()?.uid)
        } else tx
        transactions.upsert(withAccount)
    }

    suspend fun confirmTransaction(uid: String) {
        transactions.byUid(uid)?.let { transactions.upsert(it.copy(confirmed = true, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun deleteTransaction(uid: String) = transactions.softDelete(uid)
    suspend fun restoreTransaction(uid: String) = transactions.restore(uid)

    suspend fun saveTransfer(transfer: TransferEntity) = transfers.upsert(transfer)
    suspend fun deleteTransfer(uid: String) = transfers.setDeleted(uid, true)
    suspend fun restoreTransfer(uid: String) = transfers.setDeleted(uid, false)

    suspend fun saveGoal(goal: SavingsGoalEntity) = goals.upsertGoal(goal)
    suspend fun deleteGoal(uid: String) = goals.softDeleteGoal(uid)
    suspend fun addGoalContribution(contribution: GoalContributionEntity) = goals.upsertContribution(contribution)

    suspend fun saveCategory(category: CategoryEntity) = categories.upsert(category)
    /** Privzeti je lahko le en račun - ob označitvi novega se drugim zastavica odstrani. */
    suspend fun saveAccount(account: AccountEntity) = db.withTransaction {
        accounts.upsert(account)
        if (account.isDefault) accounts.clearDefaultExcept(account.uid, System.currentTimeMillis())
    }

    /** Nov vrstni red računov naenkrat, da seznami ne utripajo skozi vmesna stanja. */
    suspend fun saveAccountOrder(changed: List<AccountEntity>) = accounts.upsertAll(changed)

    /** Število (neizbrisanih) transakcij in prenosov na računu. */
    suspend fun accountUsage(uid: String): Int =
        transactions.all().count { !it.deleted && it.accountUid == uid } +
            transfers.all().count { !it.deleted && (it.fromAccountUid == uid || it.toAccountUid == uid) }

    /** Vse, kar je brisanje računa spremenilo, v stanju pred brisanjem (za razveljavitev). */
    class AccountRemoval internal constructor(
        val account: AccountEntity,
        internal val accounts: List<AccountEntity>,
        internal val transactions: List<TransactionEntity>,
        internal val transfers: List<TransferEntity>,
        internal val rules: List<RecurringRuleEntity>,
        internal val subscriptions: List<SubscriptionEntity>,
        internal val favorites: List<FavoriteEntity>,
    )

    /**
     * Izbriše račun. Z [moveTo] se transakcije, prenosi, začetno stanje, pravila, naročnine in priljubljeni
     * vnosi prenesejo na drug račun (skupno stanje ostane enako); brez njega se transakcije izbrišejo,
     * pravila, naročnine in priljubljeni pa gredo na privzeti račun. Prenosi na druge račune ostanejo,
     * ker so del stanja tistih računov.
     */
    suspend fun deleteAccount(uid: String, moveTo: String?): AccountRemoval = db.withTransaction {
        val now = System.currentTimeMillis()
        val account = requireNotNull(accounts.byUid(uid))
        val target = moveTo?.let { accounts.byUid(it) }?.takeIf { !it.deleted && it.uid != uid }
        val txs = transactions.all().filter { !it.deleted && it.accountUid == uid }
        val trs = transfers.all().filter { !it.deleted && (it.fromAccountUid == uid || it.toAccountUid == uid) }
        val rules = recurring.all().filter { !it.deleted && it.accountUid == uid }
        val subs = subscriptions.all().filter { !it.deleted && it.accountUid == uid }
        val favs = favorites.all().filter { !it.deleted && it.accountUid == uid }
        val newUid = target?.uid
        if (target != null) {
            transactions.upsertAll(txs.map { it.copy(accountUid = target.uid, updatedAt = now) })
            transfers.upsertAll(
                trs.map { t ->
                    val from = if (t.fromAccountUid == uid) target.uid else t.fromAccountUid
                    val to = if (t.toAccountUid == uid) target.uid else t.toAccountUid
                    // Prenos med združenima računoma postane notranji in nima več pomena
                    t.copy(fromAccountUid = from, toAccountUid = to, deleted = from == to, updatedAt = now)
                },
            )
            accounts.upsert(target.copy(initialBalanceCents = target.initialBalanceCents + account.initialBalanceCents, updatedAt = now))
        } else {
            transactions.upsertAll(txs.map { it.copy(deleted = true, updatedAt = now) })
        }
        recurring.upsertAll(rules.map { it.copy(accountUid = newUid, updatedAt = now) })
        subscriptions.upsertAll(subs.map { it.copy(accountUid = newUid, updatedAt = now) })
        favorites.upsertAll(favs.map { it.copy(accountUid = newUid, updatedAt = now) })
        accounts.upsert(account.copy(deleted = true, updatedAt = now))
        AccountRemoval(account, listOfNotNull(account, target), txs, if (target != null) trs else emptyList(), rules, subs, favs)
    }

    /** Razveljavi [deleteAccount]: vse spremenjeno vrne v stanje pred brisanjem. */
    suspend fun restoreAccount(removal: AccountRemoval) = db.withTransaction {
        val now = System.currentTimeMillis()
        accounts.upsertAll(removal.accounts.map { it.copy(updatedAt = now) })
        transactions.upsertAll(removal.transactions.map { it.copy(updatedAt = now) })
        transfers.upsertAll(removal.transfers.map { it.copy(updatedAt = now) })
        recurring.upsertAll(removal.rules.map { it.copy(updatedAt = now) })
        subscriptions.upsertAll(removal.subscriptions.map { it.copy(updatedAt = now) })
        favorites.upsertAll(removal.favorites.map { it.copy(updatedAt = now) })
    }
    suspend fun saveRecurringRule(rule: RecurringRuleEntity) = recurring.upsert(rule)

    suspend fun categoryByUid(uid: String?) = uid?.let { categories.byUid(it) }
    suspend fun accountByUid(uid: String?) = uid?.let { accounts.byUid(it) }
    suspend fun categoriesByType(type: TransactionType) = categories.byType(type)
    suspend fun defaultAccount() = accounts.defaultAccount()

    /**
     * Predlog kategorije: najprej iz zadnjih transakcij s podobnim komentarjem, sicer po nazivu
     * ali ključnih besedah kategorije (upošteva sklone, npr. "v trgovini" -> ključna beseda "trgovina").
     */
    suspend fun suggestCategoryForComment(comment: String, type: TransactionType? = null): String? {
        if (comment.isBlank()) return null
        val active = categories.allActive()
        val fromHistory = transactions.searchByComment(comment.trim(), limit = 10)
            .mapNotNull { it.categoryUid }
            .filter { uid -> type == null || active.any { it.uid == uid && it.type == type } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
        return fromHistory ?: WordMatch.matchCategory(comment, active, type)?.uid
    }

    fun monthRange(month: YearMonth): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>> =
        transactions.observeInRange(fromMillis, toMillis)

    suspend fun transactionsForMonth(month: YearMonth): List<TransactionEntity> {
        val (from, to) = monthRange(month)
        return transactions.inRange(from, to)
    }

    fun dateToMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Ali je bil od [since] naprej ročno vnesen kakšen strošek/prihodek (za večerni opomnik). */
    suspend fun hasManualEntrySince(since: Long): Boolean = transactions.countManualCreatedSince(since) > 0

    // ---------- Samodejno varčevanje ----------

    /**
     * Zapiše samodejna mesečna vplačila v cilje, ki so na vrsti (dan v mesecu je mimo, ta mesec še ni
     * bilo vplačila). Zamujene mesece (aplikacija ni tekla) nadoknadi, največ 12 nazaj.
     * Vplačilo ne preseže ciljnega zneska. Vrne število zapisanih vplačil.
     */
    suspend fun applyAutoSavings(today: LocalDate = LocalDate.now()): Int = chargeLock.withLock {
        var added = 0
        val contributions = goals.allContributions().filter { !it.deleted }.groupBy { it.goalUid }
        for (goal in goals.allGoals()) {
            val auto = goal.monthlyAutoCents?.takeIf { it > 0 } ?: continue
            if (goal.deleted) continue
            var saved = contributions[goal.uid].orEmpty().sumOf { it.amountCents }
            val plan = autoSavingPlan(goal.lastAutoMonth, goal.autoDay, today)
            for (month in plan.months) {
                val amount = minOf(auto, goal.targetCents - saved)
                if (amount > 0) {
                    goals.upsertContribution(
                        GoalContributionEntity(
                            // Določen uid: isto mesečno vplačilo se ne zapiše dvakrat
                            uid = UUID.nameUUIDFromBytes("auto-saving:${goal.uid}:$month".toByteArray()).toString(),
                            goalUid = goal.uid,
                            amountCents = amount,
                            date = dateToMillis(month.atDay(goal.autoDay.coerceIn(1, 28))),
                            note = str(R.string.auto_saving_note),
                        ),
                    )
                    saved += amount
                    added++
                }
            }
            val lastDone = plan.lastMonth.toString()
            if (lastDone != goal.lastAutoMonth) {
                goals.upsertGoal(goal.copy(lastAutoMonth = lastDone, updatedAt = System.currentTimeMillis()))
            }
        }
        added
    }

    // ---------- Priljubljeni vnosi ----------

    suspend fun saveFavorite(favorite: FavoriteEntity) = favorites.upsert(favorite)
    suspend fun activeFavorites() = favorites.allActive()
    suspend fun favoriteByUid(uid: String) = favorites.byUid(uid)

    /** Zapiše priljubljen vnos kot transakcijo z današnjim datumom; vrne njen uid. */
    suspend fun addFromFavorite(favorite: FavoriteEntity, date: LocalDate = LocalDate.now()): String {
        val tx = TransactionEntity(
            type = favorite.type,
            amountCents = favorite.amountCents,
            date = dateToMillis(date),
            comment = favorite.comment.ifBlank { if (favorite.categoryUid == null) favorite.title else "" },
            categoryUid = favorite.categoryUid,
            accountUid = favorite.accountUid,
        )
        saveTransaction(tx)
        return tx.uid
    }

    // ---------- Dolgovi ----------

    suspend fun saveDebt(debt: DebtEntity) = debts.upsert(debt)
    suspend fun openDebtsWithDueDate() = debts.openWithDueDate()

    // ---------- Ponavljajoča pravila ----------

    /** Zapadli termini pravila, ki še ni zapisalo samodejno (za opomnik). */
    data class DueRule(val rule: RecurringRuleEntity, val dates: List<LocalDate>)

    /**
     * Zapadle termine ponavljajočih pravil zapiše kot transakcije na datum termina - vse zamujene
     * naenkrat. Pravila s samodejnim zapisom -> potrjeno; ostala -> nepotrjeno (uporabnik potrdi na
     * Domov), vrnejo se za opomnik. Vsak termin ima določen uid, zato se nikoli ne zapiše dvakrat.
     */
    suspend fun processDueRecurringRules(today: LocalDate = LocalDate.now()): List<DueRule> = chargeLock.withLock {
        val reminders = mutableListOf<DueRule>()
        for (rule in recurring.due(dateToMillis(today))) {
            val dates = rule.occurrencesUntil(today)
            val billingDay = rule.effectiveBillingDay
            val next = dates.lastOrNull()?.let { rule.nextAfter(it) } ?: millisToDate(rule.nextDueDate)
            val ended = rule.endDate != null && dateToMillis(next) > rule.endDate
            val accountUid = rule.accountUid ?: accounts.defaultAccount()?.uid
            db.withTransaction {
                dates.forEach { date ->
                    transactions.upsert(
                        TransactionEntity(
                            uid = recurringTxUid(rule.uid, date),
                            type = rule.type,
                            amountCents = rule.amountCents,
                            date = dateToMillis(date),
                            comment = rule.title,
                            categoryUid = rule.categoryUid,
                            accountUid = accountUid,
                            source = TransactionSource.RECURRING,
                            recurringRuleUid = rule.uid,
                            confirmed = rule.autoAdd,
                        ),
                    )
                }
                recurring.upsert(
                    rule.copy(
                        nextDueDate = dateToMillis(next),
                        // Stari pravili shrani dan obračuna, da se 31. ne "zlepi" na 28.
                        billingDay = billingDay,
                        enabled = rule.enabled && !ended,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            if (!rule.autoAdd && dates.isNotEmpty()) reminders += DueRule(rule, dates)
        }
        reminders
    }

    private fun millisToDate(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    // ---------- Naročnine ----------

    suspend fun saveSubscription(subscription: SubscriptionEntity) = subscriptions.upsert(subscription)
    suspend fun activeSubscriptions() = subscriptions.allActive()

    /** Kategorija "Naročnine" (odhodek); ustvari jo ob prvi uporabi. */
    suspend fun subscriptionCategoryUid(): String {
        categoryLock.withLock {
            // Najprej naziv v jeziku aplikacije, nato slovenski (kategorija, ustvarjena pred menjavo jezika)
            listOf(SUBSCRIPTION_CATEGORY, LEGACY_SUBSCRIPTION_CATEGORY).distinct().forEach { title ->
                categories.byTitle(title)?.takeIf { it.type == TransactionType.EXPENSE }?.let { return it.uid }
            }
            val created = CategoryEntity(
                title = SUBSCRIPTION_CATEGORY,
                type = TransactionType.EXPENSE,
                position = (categories.allActive().maxOfOrNull { it.position } ?: 0) + 1,
                keywords = if (L10n.ready) str(R.string.cat_subscriptions_kw) else "naročnine,naročnina,netflix,spotify,hbo,disney,youtube,icloud,google one,microsoft 365,chatgpt",
            )
            categories.upsert(created)
            return created.uid
        }
    }

    /**
     * Zapadla plačila naročnin zapiše kot odhodke na njihov datum plačila (tudi zamujena, če
     * aplikacija nekaj dni ni tekla) in premakne naslednje plačilo naprej. Vrne število novih odhodkov.
     */
    suspend fun chargeDueSubscriptions(today: LocalDate = LocalDate.now()): Int = chargeLock.withLock {
        var charged = 0
        for (sub in subscriptions.due(dateToMillis(today))) {
            val dates = sub.paymentsUntil(today)
            if (dates.isEmpty()) continue // naročnina se je že končala
            val accountUid = sub.accountUid ?: accounts.defaultAccount()?.uid
            db.withTransaction {
                dates.forEach { date ->
                    transactions.upsert(
                        TransactionEntity(
                            // Določen uid: isto plačilo se nikoli ne zapiše dvakrat (tudi ne na drugi napravi)
                            uid = UUID.nameUUIDFromBytes("subscription:${sub.uid}:$date".toByteArray()).toString(),
                            type = TransactionType.EXPENSE,
                            amountCents = sub.amountCents,
                            currencyCode = sub.currencyCode,
                            date = dateToMillis(date),
                            comment = sub.title,
                            categoryUid = sub.categoryUid,
                            accountUid = accountUid,
                            source = TransactionSource.SUBSCRIPTION,
                            subscriptionUid = sub.uid,
                        ),
                    )
                }
                subscriptions.upsert(
                    sub.copy(nextPaymentDate = dateToMillis(sub.nextPaymentAfter(dates.last())), updatedAt = System.currentTimeMillis()),
                )
            }
            charged += dates.size
        }
        charged
    }

    companion object {
        private const val LEGACY_SUBSCRIPTION_CATEGORY = "Naročnine"

        /** Naziv samodejne kategorije za naročnine v jeziku aplikacije. */
        val SUBSCRIPTION_CATEGORY: String
            get() = if (L10n.ready) str(R.string.cat_subscriptions) else LEGACY_SUBSCRIPTION_CATEGORY

        /** Določen uid transakcije za termin pravila (isti termin = ista transakcija). */
        fun recurringTxUid(ruleUid: String, date: LocalDate): String =
            UUID.nameUUIDFromBytes("recurring:$ruleUid:$date".toByteArray()).toString()
    }
}
