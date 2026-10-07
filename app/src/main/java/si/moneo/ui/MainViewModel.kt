package si.moneo.ui

import si.moneo.R
import si.moneo.ui.str
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.moneo.MoneoApp
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.defaultAccount
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.report.PeriodReport
import si.moneo.ui.home.HomeSectionState
import si.moneo.ui.home.parseHomeLayout
import si.moneo.ui.home.serializeHomeLayout
import si.moneo.domain.subscriptions.nextPaymentAfter
import si.moneo.domain.subscriptions.nextAfter
import si.moneo.domain.subscriptions.effectiveBillingDay
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity
import si.moneo.data.prefs.AppPrefs
import si.moneo.data.repo.FinanceRepository
import java.time.LocalDate
import java.time.YearMonth

data class TransactionUi(
    val uid: String,
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val comment: String,
    val categoryUid: String?,
    val categoryTitle: String?,
    val categoryColor: Int?,
    val accountUid: String?,
    val accountTitle: String?,
    val source: TransactionSource,
    val confirmed: Boolean,
    val attachmentPath: String? = null,
    val recurringRuleUid: String? = null,
    val subscriptionUid: String? = null,
    val tags: List<String> = emptyList(),
    /** Čas nastanka zapisa (ohrani se ob urejanju). */
    val createdAt: Long = 0,
) {
    val title: String get() = categoryTitle ?: comment.ifBlank { str(R.string.no_category) }
}

data class HomeUiState(
    val transactions: List<TransactionUi> = emptyList(),
    val unconfirmed: List<TransactionUi> = emptyList(),
    val expenseCents: Long = 0,
    val incomeCents: Long = 0,
    val period: StatsPeriod = StatsPeriod.MONTH,
    /** Začetek izbranega obdobja (vključno). */
    val start: LocalDate = LocalDate.now().withDayOfMonth(1),
    /** true, ko je izbrano tekoče obdobje (naprej ne gre). */
    val isCurrent: Boolean = true,
    /** Povprečni prihodek/odhodek na dan za izbrano obdobje. */
    val average: DailyAverage = DailyAverage(0, 0, 1, ""),
    /** Neto zadnjih 6 obdobij izbrane dolžine (za trend). */
    val trend: List<Long> = emptyList(),
    val accounts: List<AccountBalance> = emptyList(),
    val budgetAlerts: List<BudgetStatus> = emptyList(),
    val transfers: List<TransferUi> = emptyList(),
    val loaded: Boolean = false,
)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val buckets: List<PeriodBucket> = emptyList(),
    val selectedIndex: Int = 0,
    val spends: List<CategorySpend> = emptyList(),
    /** Prihodki po kategorijah (tab "Prihodki" v Statistiki). */
    val incomeSpends: List<CategorySpend> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val budgets: List<BudgetStatus> = emptyList(),
    /** Isto obdobje lani (pri tekočem obdobju do istega dne). */
    val lastYear: LastYearTotals? = null,
    /** Lanske vsote po kategorijah v istem obdobju, ključ = [categoryKey]. */
    val lastYearByCategory: Map<String, Long> = emptyMap(),
) {
    val selected: PeriodBucket? get() = buckets.getOrNull(selectedIndex)
    val previous: PeriodBucket? get() = buckets.getOrNull(selectedIndex - 1)
}

data class SearchFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val categoryUid: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val accountUid: String? = null,
    val tag: String? = null,
) {
    /** Število nastavljenih naprednih filtrov (obdobje šteje kot en filter). */
    val advancedCount: Int get() =
        listOf(from != null || to != null, minCents != null || maxCents != null, accountUid != null, tag != null).count { it }
    val isActive: Boolean get() = query.isNotBlank() || type != null || categoryUid != null || advancedCount > 0
}

/** Zadetki iskanja: prikazanih največ [MAX_SHOWN], vsote pa čez vse zadetke. */
data class SearchResult(
    val items: List<TransactionUi> = emptyList(),
    val total: Int = 0,
    val expenseCents: Long = 0,
    val incomeCents: Long = 0,
) {
    companion object { const val MAX_SHOWN = 300 }
}

data class CategoryDetailState(
    val category: CategoryEntity? = null,
    val trend: List<PeriodBucket> = emptyList(),
    val transactions: List<TransactionUi> = emptyList(),
    val monthSpentCents: Long = 0,
    /** Letos proti istemu obdobju lani (od 1. januarja do danes) in po mesecih. */
    val yearComparison: CategoryYearComparison? = null,
)

class MainViewModel(private val repo: FinanceRepository, private val prefs: AppPrefs) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    /** Obdobje na Domov (dan / teden / mesec / leto) in poljuben datum znotraj izbranega obdobja. */
    val homePeriod = MutableStateFlow(StatsPeriod.MONTH)
    private val homeAnchor = MutableStateFlow(LocalDate.now())
    private val homeRange = combine(homePeriod, homeAnchor) { p, a -> p to p.startOf(a) }

    val accounts: StateFlow<List<AccountEntity>> = repo.allAccounts.state(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repo.allCategories.state(emptyList())

    /** Vse transakcije, obogatene z nazivi/barvami - osnova za vse izračune. */
    val allTransactions: StateFlow<List<TransactionUi>> = combine(
        repo.allTransactions, accounts, categories,
    ) { txs, accs, cats ->
        val accByUid = accs.associateBy { it.uid }
        val catByUid = cats.associateBy { it.uid }
        txs.map { tx ->
            val cat = catByUid[tx.categoryUid]
            TransactionUi(
                uid = tx.uid, type = tx.type, amountCents = tx.amountCents,
                date = millisToLocalDate(tx.date), comment = tx.comment,
                categoryUid = tx.categoryUid, categoryTitle = cat?.title, categoryColor = cat?.color,
                accountUid = tx.accountUid, accountTitle = accByUid[tx.accountUid]?.title,
                source = tx.source, confirmed = tx.confirmed,
                attachmentPath = tx.attachmentPath,
                recurringRuleUid = tx.recurringRuleUid,
                subscriptionUid = tx.subscriptionUid,
                tags = parseTags(tx.tags),
                createdAt = tx.createdAt,
            )
        }
    }.state(emptyList())

    /** Izbran račun za filtriranje Domov/Statistike (null = vsi računi). */
    val accountFilter = MutableStateFlow<String?>(null)

    /** Transakcije po filtru računa (transakcija brez računa pripada privzetemu). */
    private val filteredTransactions: StateFlow<List<TransactionUi>> = combine(allTransactions, accountFilter, accounts) { txs, f, accs ->
        if (f == null) txs else {
            val def = accs.defaultAccount()?.uid
            txs.filter { (it.accountUid ?: def) == f }
        }
    }.state(emptyList())

    private val balances: StateFlow<List<AccountBalance>> = combine(accounts, allTransactions, repo.allTransfers) { accs, txs, tr ->
        accountBalances(accs, txs, tr)
    }.state(emptyList())

    private val transfersUi: StateFlow<List<TransferUi>> = combine(repo.allTransfers, accounts, accountFilter) { tr, accs, f ->
        val titles = accs.associate { it.uid to it.title }
        tr.filter { f == null || it.fromAccountUid == f || it.toAccountUid == f }.map { it.toUi(titles) }
    }.state(emptyList())

    val home: StateFlow<HomeUiState> = combine(
        homeRange, filteredTransactions, balances, transfersUi, categories,
    ) { (period, start), txs, bals, transfers, cats ->
        val end = period.plus(start, 1)
        fun inRange(d: LocalDate) = !d.isBefore(start) && d.isBefore(end)
        val inPeriod = txs.filter { inRange(it.date) }
        val today = LocalDate.now()
        // Opozorila proračuna so mesečna - vedno za tekoči mesec
        val thisMonth = YearMonth.from(today)
        HomeUiState(
            transactions = inPeriod,
            unconfirmed = txs.filter { !it.confirmed },
            expenseCents = inPeriod.filter { it.type == TransactionType.EXPENSE && it.confirmed }.sumOf { it.amountCents },
            incomeCents = inPeriod.filter { it.type == TransactionType.INCOME && it.confirmed }.sumOf { it.amountCents },
            period = period,
            start = start,
            isCurrent = start == period.startOf(today),
            average = dailyAverage(txs, period, start, end, today),
            trend = buildBuckets(txs, period, BUCKETS, start).map { it.netCents },
            accounts = bals,
            budgetAlerts = budgetStatuses(cats, txs.filter { YearMonth.from(it.date) == thisMonth }).filter { it.fraction >= 0.8f },
            transfers = transfers.filter { inRange(it.date) },
            loaded = true,
        )
    }.state(HomeUiState())

    private var lastKnownToday = LocalDate.now()

    /**
     * Ob vrnitvi v aplikacijo: če je minila polnoč in je Domov kazal tekoče obdobje (npr. "Danes"),
     * se premakne na novo tekoče obdobje. Če je uporabnik gledal starejše obdobje, ostane tam.
     */
    fun onAppResumed() {
        val today = LocalDate.now()
        if (today == lastKnownToday) return
        val p = homePeriod.value
        if (p.startOf(homeAnchor.value) == p.startOf(lastKnownToday)) homeAnchor.value = today
        lastKnownToday = today
    }

    fun setHomePeriod(p: StatsPeriod) {
        homePeriod.value = p
        homeAnchor.value = LocalDate.now()
    }

    fun prevPeriod() { homeAnchor.value = homePeriod.value.plus(homePeriod.value.startOf(homeAnchor.value), -1) }

    fun nextPeriod() {
        val p = homePeriod.value
        val next = p.plus(p.startOf(homeAnchor.value), 1)
        if (!next.isAfter(LocalDate.now())) homeAnchor.value = next
    }

    /** Poraba po kategorijah v tekočem mesecu (za seznam kategorij). */
    val currentMonthByCategory: StateFlow<Map<String?, Long>> = allTransactions.map { txs ->
        val m = YearMonth.now()
        txs.filter { it.confirmed && YearMonth.from(it.date) == m }
            .groupBy { it.categoryUid }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
    }.state(emptyMap())

    // ---------- Statistika ----------

    val statsPeriod = MutableStateFlow(StatsPeriod.MONTH)
    /** null = zadnje (tekoče) obdobje. */
    val statsSelection = MutableStateFlow<Int?>(null)

    val stats: StateFlow<StatsUiState> = combine(
        filteredTransactions, categories, statsPeriod, statsSelection,
    ) { txs, cats, period, sel ->
        val today = LocalDate.now()
        val buckets = buildBuckets(txs, period, BUCKETS, today)
        val index = (sel ?: buckets.lastIndex).coerceIn(0, buckets.lastIndex)
        val bucket = buckets[index]
        val inBucket = txs.filter { it.date in bucket }
        val spends = categorySpends(inBucket, cats)
        StatsUiState(
            period = period,
            buckets = buckets,
            selectedIndex = index,
            spends = spends,
            incomeSpends = categorySpends(inBucket, cats, TransactionType.INCOME),
            insights = buildInsights(period, bucket, buckets.getOrNull(index - 1), spends, inBucket, today) +
                yearOverYearInsights(txs, cats, bucket, today),
            lastYear = lastYearTotals(txs, bucket, today).takeIf { it.hasData },
            lastYearByCategory = lastYearByCategory(txs, bucket, today),
            budgets = if (period == StatsPeriod.MONTH) budgetStatuses(cats, inBucket) else emptyList(),
        )
    }.state(StatsUiState())

    fun setStatsPeriod(p: StatsPeriod) {
        statsPeriod.value = p
        statsSelection.value = null
    }

    fun selectBucket(i: Int) { statsSelection.value = i }

    /** Podatki za PDF poročilo obdobja, izbranega v Statistiki (upošteva filter računa). */
    fun periodReport(): PeriodReport? {
        val s = stats.value
        val bucket = s.selected ?: return null
        val inBucket = filteredTransactions.value.filter { it.date in bucket }
        return PeriodReport(
            periodLabel = bucket.longLabel,
            accountLabel = accountFilter.value?.let { f -> accounts.value.firstOrNull { it.uid == f }?.title },
            bucket = bucket,
            previous = s.previous,
            history = s.buckets,
            expenses = s.spends,
            income = s.incomeSpends,
            budgets = s.budgets.associate { it.category.uid to it.budgetCents },
            biggest = inBucket.filter { it.type == TransactionType.EXPENSE && it.confirmed }.sortedByDescending { it.amountCents }.take(10),
            transactionCount = inBucket.size,
        )
    }

    // ---------- Cilji ----------

    val goals: StateFlow<List<GoalUi>> = combine(repo.allGoals, repo.allGoalContributions) { g, c ->
        buildGoals(g, c, LocalDate.now())
    }.state(emptyList())

    fun saveGoal(goal: SavingsGoalEntity) = viewModelScope.launch {
        repo.saveGoal(goal.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteGoal(uid: String) = viewModelScope.launch { repo.deleteGoal(uid) }

    fun contribute(goalUid: String, cents: Long, note: String = "") = viewModelScope.launch {
        repo.addGoalContribution(
            GoalContributionEntity(goalUid = goalUid, amountCents = cents, date = System.currentTimeMillis(), note = note),
        )
    }

    // ---------- Iskanje ----------

    val searchFilter = MutableStateFlow(SearchFilter())

    val searchResults: StateFlow<SearchResult> = combine(allTransactions, searchFilter, accounts) { txs, f, accs ->
        val q = f.query.trim().lowercase()
        if (!f.isActive) return@combine SearchResult()
        val qCents = parseCents(q)?.takeIf { q.any(Char::isDigit) }
        val defaultAccount = accs.defaultAccount()?.uid
        val matches = txs.asSequence()
            .filter { f.type == null || it.type == f.type }
            .filter { f.categoryUid == null || it.categoryUid == f.categoryUid }
            .filter { f.from == null || !it.date.isBefore(f.from) }
            .filter { f.to == null || !it.date.isAfter(f.to) }
            .filter { f.minCents == null || it.amountCents >= f.minCents }
            .filter { f.maxCents == null || it.amountCents <= f.maxCents }
            // transakcija brez računa pripada privzetemu računu
            .filter { f.accountUid == null || (it.accountUid ?: defaultAccount) == f.accountUid }
            .filter { f.tag == null || it.tags.any { t -> t.equals(f.tag, ignoreCase = true) } }
            .filter {
                q.isEmpty() ||
                    it.comment.lowercase().contains(q) ||
                    (it.categoryTitle?.lowercase()?.contains(q) == true) ||
                    (it.accountTitle?.lowercase()?.contains(q) == true) ||
                    it.tags.any { t -> t.lowercase().contains(q) } ||
                    (qCents != null && it.amountCents == qCents)
            }
            .toList()
        val confirmed = matches.filter { it.confirmed }
        SearchResult(
            items = matches.take(SearchResult.MAX_SHOWN),
            total = matches.size,
            expenseCents = confirmed.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
            incomeCents = confirmed.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
        )
    }.state(SearchResult())

    // ---------- Kategorije ----------

    /** Število vnosov na kategorijo v zadnjih dveh mesecih (za vrstni red kategorij pri vnosu). */
    val categoryUsage: StateFlow<Map<String, Int>> = combine(allTransactions, categories) { txs, _ ->
        val since = LocalDate.now().minusMonths(2)
        txs.asSequence().filter { !it.date.isBefore(since) && it.categoryUid != null }
            .groupingBy { it.categoryUid!! }.eachCount()
    }.state(emptyMap())

    fun categoryDetail(uid: String): Flow<CategoryDetailState> =
        combine(allTransactions, categories) { txs, cats ->
            val cat = cats.firstOrNull { it.uid == uid }
            val own = txs.filter { it.categoryUid == uid }
            val thisMonth = YearMonth.now()
            CategoryDetailState(
                category = cat,
                trend = buildBuckets(own, StatsPeriod.MONTH, BUCKETS, LocalDate.now()),
                transactions = own,
                monthSpentCents = own.filter { YearMonth.from(it.date) == thisMonth && it.confirmed }.sumOf { it.amountCents },
                yearComparison = categoryYearComparison(own, LocalDate.now()),
            )
        }.flowOn(Dispatchers.Default)

    fun saveCategory(category: CategoryEntity) = viewModelScope.launch {
        repo.saveCategory(category.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Soft delete - obstoječe transakcije ostanejo, le brez kategorije. */
    fun deleteCategory(category: CategoryEntity) = saveCategory(category.copy(deleted = true))

    // ---------- Računi ----------

    fun saveAccount(account: AccountEntity) = viewModelScope.launch {
        repo.saveAccount(account.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Število transakcij in prenosov na računu (pred brisanjem). */
    suspend fun accountUsage(account: AccountEntity): Int = repo.accountUsage(account.uid)

    /** Izbriše račun in transakcije prenese na [moveTo] (ali jih izbriše); vrne stanje za razveljavitev. */
    suspend fun deleteAccount(account: AccountEntity, moveTo: String?): FinanceRepository.AccountRemoval {
        if (accountFilter.value == account.uid) accountFilter.value = null
        return repo.deleteAccount(account.uid, moveTo)
    }

    fun restoreAccount(removal: FinanceRepository.AccountRemoval) = viewModelScope.launch { repo.restoreAccount(removal) }

    /** Nov vrstni red računov (uid-ji od prvega do zadnjega); velja povsod (Domov, izbirniki, statistika). */
    fun reorderAccounts(uids: List<String>) = viewModelScope.launch {
        val byUid = accounts.value.associateBy { it.uid }
        // Računi, ki jih seznam ne omenja (npr. dodani med vlečenjem), ostanejo na koncu
        val reordered = uids.mapNotNull(byUid::get) + accounts.value.filter { it.uid !in uids }
        val now = System.currentTimeMillis()
        repo.saveAccountOrder(reordered.mapIndexedNotNull { index, a -> if (a.position != index) a.copy(position = index, updatedAt = now) else null })
    }

    // ---------- Prenosi ----------

    /** true, ko je list za vnos v načinu "Prenos". */
    val transferMode = MutableStateFlow(false)
    val transferDraft = MutableStateFlow(TransferEntity(fromAmountCents = 0, date = System.currentTimeMillis()))

    fun startTransfer(prefill: TransferUi? = null) {
        draftIsEdit.value = prefill != null
        transferMode.value = true
        val accs = accounts.value
        transferDraft.value = if (prefill != null) {
            TransferEntity(
                uid = prefill.uid, fromAccountUid = prefill.fromUid, toAccountUid = prefill.toUid,
                fromAmountCents = prefill.amountCents, date = repo.dateToMillis(prefill.date), comment = prefill.comment,
            )
        } else {
            TransferEntity(
                fromAccountUid = accs.getOrNull(0)?.uid, toAccountUid = accs.getOrNull(1)?.uid,
                fromAmountCents = 0, date = System.currentTimeMillis(),
            )
        }
    }

    fun saveTransfer() = viewModelScope.launch {
        val t = transferDraft.value
        if (t.fromAmountCents > 0 && t.fromAccountUid != null && t.toAccountUid != null && t.fromAccountUid != t.toAccountUid) {
            repo.saveTransfer(t.copy(toAmountCents = t.fromAmountCents, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteTransfer(uid: String) = viewModelScope.launch { repo.deleteTransfer(uid) }
    fun restoreTransfer(uid: String) = viewModelScope.launch { repo.restoreTransfer(uid) }

    // ---------- Vnos / urejanje ----------

    val draft = MutableStateFlow(TransactionEntity(type = TransactionType.EXPENSE, amountCents = 0, date = System.currentTimeMillis()))

    /** true, ko urejamo obstoječo transakcijo (ne nove). */
    val draftIsEdit = MutableStateFlow(false)

    fun startDraft(type: TransactionType, prefill: TransactionEntity? = null) {
        transferMode.value = false
        draftIsEdit.value = prefill != null
        draft.value = prefill ?: TransactionEntity(
            type = type,
            amountCents = 0,
            date = System.currentTimeMillis(),
        )
    }

    fun editTransaction(tx: TransactionUi) = startDraft(
        tx.type,
        prefill = TransactionEntity(
            uid = tx.uid,
            type = tx.type,
            amountCents = tx.amountCents,
            date = repo.dateToMillis(tx.date),
            comment = tx.comment,
            categoryUid = tx.categoryUid,
            accountUid = tx.accountUid,
            source = tx.source,
            confirmed = tx.confirmed,
            attachmentPath = tx.attachmentPath,
            // Povezave, oznake in čas nastanka se ob urejanju ohranijo
            createdAt = tx.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            recurringRuleUid = tx.recurringRuleUid,
            subscriptionUid = tx.subscriptionUid,
            tags = joinTags(tx.tags),
        ),
    )

    /**
     * Shrani osnutek razdeljen na več kategorij: prvi del ohrani uid osnutka (pri urejanju ga posodobi),
     * ostali so novi vnosi z istim datumom, računom, opombo in oznakami.
     */
    fun saveSplit(parts: List<Pair<String?, Long>>) {
        val base = draft.value
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            parts.filter { it.second > 0 }.forEachIndexed { i, (categoryUid, cents) ->
                val first = i == 0
                repo.saveTransaction(
                    base.copy(
                        uid = if (first) base.uid else java.util.UUID.randomUUID().toString(),
                        amountCents = cents,
                        categoryUid = categoryUid,
                        attachmentPath = if (first) base.attachmentPath else null,
                        recurringRuleUid = if (first) base.recurringRuleUid else null,
                        subscriptionUid = if (first) base.subscriptionUid else null,
                        confirmed = true,
                        createdAt = if (first) base.createdAt else now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    /** Nov vnos po vzoru obstoječega (znesek, kategorija, račun, opomba, oznake) z današnjim datumom. */
    fun repeatTransaction(tx: TransactionUi) {
        transferMode.value = false
        draftIsEdit.value = false
        draft.value = TransactionEntity(
            type = tx.type,
            amountCents = tx.amountCents,
            date = System.currentTimeMillis(),
            comment = tx.comment,
            categoryUid = tx.categoryUid,
            accountUid = tx.accountUid,
            tags = joinTags(tx.tags),
        )
    }

    /**
     * Shrani vnos; vrne znesek zaokrožitve, ki je šel v cilj (0 = nič).
     * [repeat] != null ustvari še ponavljajoče pravilo (naslednja ponovitev po izbrani frekvenci).
     */
    fun saveDraft(repeat: RecurrenceFrequency? = null, autoAdd: Boolean = true): Long {
        val base = draft.value
        val rule = if (repeat != null && !draftIsEdit.value && base.amountCents > 0) {
            val date = millisToLocalDate(base.date)
            val next = nextPaymentAfter(date, repeat, 1, date.dayOfMonth)
            RecurringRuleEntity(
                title = base.comment.trim().ifEmpty { categories.value.firstOrNull { it.uid == base.categoryUid }?.title ?: str(R.string.recurring_default) },
                type = base.type,
                amountCents = base.amountCents,
                categoryUid = base.categoryUid,
                accountUid = base.accountUid,
                frequency = repeat,
                nextDueDate = repo.dateToMillis(next),
                billingDay = date.dayOfMonth,
                autoAdd = autoAdd,
            )
        } else null
        val d = if (rule != null) base.copy(recurringRuleUid = rule.uid) else base
        if (d.amountCents <= 0) return 0
        val isNew = !draftIsEdit.value
        val goalUid = prefs.roundUpGoalUid?.takeIf { uid -> goals.value.any { it.goal.uid == uid } }
        val roundUp = if (isNew && d.type == TransactionType.EXPENSE && goalUid != null) {
            (100 - d.amountCents % 100) % 100
        } else 0
        viewModelScope.launch {
            repo.saveTransaction(d.copy(confirmed = true, updatedAt = System.currentTimeMillis()))
            rule?.let { repo.saveRecurringRule(it) }
            if (roundUp > 0 && goalUid != null) {
                repo.addGoalContribution(
                    GoalContributionEntity(goalUid = goalUid, amountCents = roundUp, date = d.date, note = str(R.string.roundup_note, d.comment.ifBlank { str(R.string.type_expense) })),
                )
            }
        }
        return roundUp
    }

    // ---------- Postavitev Domov ----------

    val homeLayout = MutableStateFlow(parseHomeLayout(prefs.homeLayout))

    fun setHomeLayout(layout: List<HomeSectionState>) {
        prefs.homeLayout = serializeHomeLayout(layout)
        homeLayout.value = layout
    }

    /** Seznam na Domov: po datumu (true) ali po kategorijah (false). */
    val homeListByDate = MutableStateFlow(prefs.homeListByDate)

    fun setHomeListByDate(byDate: Boolean) {
        prefs.homeListByDate = byDate
        homeListByDate.value = byDate
    }

    // ---------- Skrivanje stanja ----------

    val hideBalance = MutableStateFlow(prefs.hideBalance)

    fun setHideBalance(hide: Boolean) {
        prefs.hideBalance = hide
        hideBalance.value = hide
    }

    /** Enkratni namig na zaslonu Računi, da se kartice da vleči. */
    val showAccountReorderHint = MutableStateFlow(!prefs.accountReorderHintShown)

    fun dismissAccountReorderHint() {
        prefs.accountReorderHintShown = true
        showAccountReorderHint.value = false
    }

    // ---------- Zaokroževanje v cilj ----------

    val roundUpGoalUid = MutableStateFlow(prefs.roundUpGoalUid)

    fun setRoundUpGoal(uid: String?) {
        prefs.roundUpGoalUid = uid
        roundUpGoalUid.value = uid
    }

    // ---------- Priponke ----------

    /** Odstrani povezavo na sliko (datoteko pustimo - urejanje se lahko še prekliče). */
    fun removeAttachment() {
        draft.value = draft.value.copy(attachmentPath = null)
    }

    suspend fun suggestCategory(comment: String, type: TransactionType? = null): String? = repo.suggestCategoryForComment(comment, type)

    fun delete(uid: String) = viewModelScope.launch { repo.deleteTransaction(uid) }
    fun restore(uid: String) = viewModelScope.launch { repo.restoreTransaction(uid) }
    fun confirm(uid: String) = viewModelScope.launch { repo.confirmTransaction(uid) }

    // ---------- Ponavljajoče ----------

    val activeRules: StateFlow<List<RecurringRuleEntity>> = repo.activeRecurringRules.state(emptyList())

    /** Shrani pravilo in takoj zapiše termine, ki so že zapadli (npr. prvi termin danes). */
    fun saveRule(rule: RecurringRuleEntity) = viewModelScope.launch {
        repo.saveRecurringRule(rule.copy(updatedAt = System.currentTimeMillis()))
        repo.processDueRecurringRules()
    }

    /**
     * Vklopi/izklopi pravilo. Ob ponovnem vklopu se termini iz časa, ko je bilo izklopljeno,
     * ne zapišejo - naslednji termin se premakne na prvega od danes naprej.
     */
    fun toggleRule(rule: RecurringRuleEntity, enabled: Boolean) = viewModelScope.launch {
        var next = millisToLocalDate(rule.nextDueDate)
        val today = LocalDate.now()
        if (enabled) while (next.isBefore(today)) next = rule.nextAfter(next)
        repo.saveRecurringRule(
            rule.copy(
                enabled = enabled, nextDueDate = repo.dateToMillis(next),
                billingDay = rule.effectiveBillingDay, updatedAt = System.currentTimeMillis(),
            ),
        )
        if (enabled) repo.processDueRecurringRules()
    }

    fun deleteRule(rule: RecurringRuleEntity) = viewModelScope.launch {
        repo.saveRecurringRule(rule.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }

    /** Predlogi zaznanih naročnin, ki jih uporabnik ni zavrnil v tej seji. */
    private val dismissedSuggestions = MutableStateFlow(emptySet<String>())

    val subscriptionSuggestions: StateFlow<List<SubscriptionSuggestion>> = combine(
        allTransactions, activeRules, dismissedSuggestions, repo.allSubscriptions,
    ) { txs, rules, dismissed, subs ->
        val subTitles = subs.map { it.title.lowercase() }.toSet()
        detectSubscriptions(txs, rules, LocalDate.now())
            .filter { it.key !in dismissed && it.title.lowercase() !in subTitles }
    }.state(emptyList())

    fun dismissSuggestion(s: SubscriptionSuggestion) { dismissedSuggestions.value += s.key }

    fun acceptSuggestion(s: SubscriptionSuggestion) = viewModelScope.launch {
        // Naslednji termin na isti dan v mesecu kot zadnje plačilo (prvi od danes naprej)
        val day = s.lastDate.dayOfMonth
        var next = nextPaymentAfter(s.lastDate, RecurrenceFrequency.MONTHLY, 1, day)
        while (next.isBefore(LocalDate.now())) next = nextPaymentAfter(next, RecurrenceFrequency.MONTHLY, 1, day)
        repo.saveRecurringRule(
            RecurringRuleEntity(
                title = s.title, type = s.type, amountCents = s.amountCents,
                categoryUid = s.categoryUid, accountUid = s.accountUid,
                frequency = RecurrenceFrequency.MONTHLY,
                nextDueDate = repo.dateToMillis(next), billingDay = day, autoAdd = false,
            ),
        )
    }


    // ---------- Priljubljeni vnosi ----------

    val favorites: StateFlow<List<FavoriteEntity>> = repo.allFavorites.state(emptyList())

    fun saveFavorite(favorite: FavoriteEntity) = viewModelScope.launch {
        val position = if (favorites.value.any { it.uid == favorite.uid }) favorite.position
        else (favorites.value.maxOfOrNull { it.position } ?: 0) + 1
        repo.saveFavorite(favorite.copy(position = position, updatedAt = System.currentTimeMillis()))
    }

    fun deleteFavorite(favorite: FavoriteEntity) = viewModelScope.launch {
        repo.saveFavorite(favorite.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }

    /** Nov vrstni red priljubljenih vnosov (uid-ji od prvega do zadnjega). */
    fun reorderFavorites(uids: List<String>) = viewModelScope.launch {
        val list = favorites.value
        val byUid = list.associateBy { it.uid }
        // Vnosi, ki jih seznam ne omenja (npr. dodani med vlečenjem), ostanejo na koncu
        val reordered = uids.mapNotNull(byUid::get) + list.filter { it.uid !in uids }
        val now = System.currentTimeMillis()
        reordered.forEachIndexed { index, f -> if (f.position != index) repo.saveFavorite(f.copy(position = index, updatedAt = now)) }
    }

    /** Doda transakcijo iz priljubljenega vnosa (danes); vrne uid za razveljavitev. */
    suspend fun addFromFavorite(favorite: FavoriteEntity): String = repo.addFromFavorite(favorite)

    /** Trenutni osnutek vnosa shrani kot priljubljen vnos. */
    fun saveDraftAsFavorite(title: String) {
        val d = draft.value
        if (d.amountCents <= 0) return
        saveFavorite(
            FavoriteEntity(
                title = title.trim(), type = d.type, amountCents = d.amountCents,
                categoryUid = d.categoryUid, accountUid = d.accountUid, comment = d.comment.trim(),
            ),
        )
    }

    // ---------- Oznake ----------

    val tags: StateFlow<List<TagSummary>> = allTransactions.map { tagSummaries(it) }.state(emptyList())

    fun tagDetail(tag: String): Flow<TagDetailState> = combine(allTransactions, categories) { txs, cats ->
        val own = txs.filter { tx -> tx.tags.any { it.equals(tag, ignoreCase = true) } }
        TagDetailState(
            tag = tag,
            summary = tagSummaries(own).firstOrNull { it.tag.equals(tag, ignoreCase = true) },
            transactions = own,
            byCategory = categorySpends(own, cats),
        )
    }.flowOn(Dispatchers.Default)

    // ---------- Dolgovi ----------

    val debts: StateFlow<List<DebtEntity>> = repo.allDebts.state(emptyList())

    fun saveDebt(debt: DebtEntity) = viewModelScope.launch {
        repo.saveDebt(debt.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteDebt(debt: DebtEntity) = viewModelScope.launch {
        repo.saveDebt(debt.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }

    // ---------- Skupni proračun ----------

    val monthlyBudget = MutableStateFlow(prefs.monthlyBudgetCents)

    fun setMonthlyBudget(cents: Long?) {
        prefs.monthlyBudgetCents = cents?.takeIf { it > 0 }
        monthlyBudget.value = prefs.monthlyBudgetCents
    }

    /** Skupni proračun tekočega meseca (vsi računi, ne glede na filter). */
    val overallBudget: StateFlow<OverallBudget?> = combine(allTransactions, monthlyBudget) { txs, budget ->
        overallBudget(txs, budget, LocalDate.now())
    }.state(null)

    // ---------- Letni pregled ----------

    fun yearReview(year: Int): Flow<YearReview> = combine(allTransactions, categories) { txs, cats ->
        buildYearReview(txs, cats, year, LocalDate.now())
    }.flowOn(Dispatchers.Default)

    // ---------- Naročnine ----------

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repo.allSubscriptions.state(emptyList())

    /** Naročnine, ponavljajoča plačila in roki dolgov v naslednjih 7 dneh (za Domov). */
    val upcoming: StateFlow<List<UpcomingPayment>> = combine(subscriptions, activeRules, debts) { subs, rules, d ->
        upcomingPayments(subs, rules, d, LocalDate.now())
    }.state(emptyList())

    /** Shrani naročnino (brez kategorije -> "Naročnine") in takoj zapiše plačila, ki so že zapadla. */
    fun saveSubscription(sub: SubscriptionEntity) = viewModelScope.launch {
        val withCategory = if (sub.categoryUid == null) sub.copy(categoryUid = repo.subscriptionCategoryUid()) else sub
        repo.saveSubscription(withCategory.copy(updatedAt = System.currentTimeMillis()))
        repo.chargeDueSubscriptions()
    }

    /**
     * Ustavi ali nadaljuje naročnino. Ob nadaljevanju se plačila iz časa premora ne zapišejo -
     * naslednje plačilo se premakne na prvi termin od danes naprej.
     */
    fun setSubscriptionActive(sub: SubscriptionEntity, active: Boolean) = viewModelScope.launch {
        var next = millisToLocalDate(sub.nextPaymentDate)
        val today = LocalDate.now()
        if (active) while (next.isBefore(today)) next = sub.nextPaymentAfter(next)
        repo.saveSubscription(sub.copy(active = active, nextPaymentDate = repo.dateToMillis(next), updatedAt = System.currentTimeMillis()))
        if (active) repo.chargeDueSubscriptions()
    }

    /** Izbriše naročnino; že zapisani odhodki ostanejo. */
    fun deleteSubscription(sub: SubscriptionEntity) = viewModelScope.launch {
        repo.saveSubscription(sub.copy(deleted = true, updatedAt = System.currentTimeMillis()))
    }

    companion object {
        const val BUCKETS = 6

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneoApp
                return MainViewModel(app.container.repository, app.container.prefs) as T
            }
        }
    }
}
