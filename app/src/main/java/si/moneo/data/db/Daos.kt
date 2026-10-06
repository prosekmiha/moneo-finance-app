package si.moneo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity

@Dao
interface AccountDao {
    @Upsert suspend fun upsert(account: AccountEntity)
    @Upsert suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY position, title")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE uid = :uid")
    suspend fun byUid(uid: String): AccountEntity?

    /** Označen privzeti račun; če ga ni (ali je izbrisan), prvi po vrstnem redu. */
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY isDefault DESC, position, title LIMIT 1")
    suspend fun defaultAccount(): AccountEntity?

    @Query("UPDATE accounts SET isDefault = 0, updatedAt = :now WHERE isDefault = 1 AND uid != :uid")
    suspend fun clearDefaultExcept(uid: String, now: Long)

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>
}

@Dao
interface CategoryDao {
    @Upsert suspend fun upsert(category: CategoryEntity)
    @Upsert suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY position, title")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0")
    suspend fun allActive(): List<CategoryEntity>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE uid = :uid")
    suspend fun byUid(uid: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE deleted = 0 AND title = :title COLLATE NOCASE LIMIT 1")
    suspend fun byTitle(title: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE deleted = 0 AND type = :type ORDER BY title")
    suspend fun byType(type: TransactionType): List<CategoryEntity>

    @Query("UPDATE categories SET title = :newTitle, updatedAt = :now WHERE title = :oldTitle")
    suspend fun rename(oldTitle: String, newTitle: String, now: Long = System.currentTimeMillis())
}

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(transaction: TransactionEntity)
    @Upsert suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND confirmed = 0 ORDER BY date DESC")
    fun observeUnconfirmed(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND date >= :fromMillis AND date < :toMillis ORDER BY date DESC")
    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND date >= :fromMillis AND date < :toMillis")
    suspend fun inRange(fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND comment LIKE '%' || :text || '%' ORDER BY date DESC LIMIT :limit")
    suspend fun searchByComment(text: String, limit: Int = 20): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE uid = :uid")
    suspend fun byUid(uid: String): TransactionEntity?

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE uid = :uid")
    suspend fun softDelete(uid: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deleted = 0, updatedAt = :now WHERE uid = :uid")
    suspend fun restore(uid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    /** Ročni vnosi (ne samodejne naročnine/ponavljanja), ustvarjeni od [since] naprej. */
    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND createdAt >= :since AND source NOT IN ('SUBSCRIPTION', 'RECURRING')")
    suspend fun countManualCreatedSince(since: Long): Int
}

@Dao
interface TransferDao {
    @Upsert suspend fun upsert(transfer: TransferEntity)
    @Upsert suspend fun upsertAll(transfers: List<TransferEntity>)

    @Query("SELECT * FROM transfers WHERE deleted = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers")
    suspend fun all(): List<TransferEntity>

    @Query("UPDATE transfers SET deleted = :deleted, updatedAt = :now WHERE uid = :uid")
    suspend fun setDeleted(uid: String, deleted: Boolean, now: Long = System.currentTimeMillis())
}

@Dao
interface RecurringRuleDao {
    @Upsert suspend fun upsert(rule: RecurringRuleEntity)

    @Query("SELECT * FROM recurring_rules WHERE deleted = 0 ORDER BY nextDueDate")
    fun observeActive(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE deleted = 0 AND enabled = 1 AND nextDueDate <= :nowMillis")
    suspend fun due(nowMillis: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE uid = :uid")
    suspend fun byUid(uid: String): RecurringRuleEntity?

    @Upsert suspend fun upsertAll(rules: List<RecurringRuleEntity>)

    @Query("SELECT * FROM recurring_rules")
    suspend fun all(): List<RecurringRuleEntity>
}

@Dao
interface GoalDao {
    @Upsert suspend fun upsertGoal(goal: SavingsGoalEntity)
    @Upsert suspend fun upsertContribution(contribution: GoalContributionEntity)

    @Query("SELECT * FROM savings_goals WHERE deleted = 0 ORDER BY createdAt")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM goal_contributions WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun observeContributions(): Flow<List<GoalContributionEntity>>

    @Query("UPDATE savings_goals SET deleted = 1, updatedAt = :now WHERE uid = :uid")
    suspend fun softDeleteGoal(uid: String, now: Long = System.currentTimeMillis())

    @Upsert suspend fun upsertGoals(goals: List<SavingsGoalEntity>)
    @Upsert suspend fun upsertContributions(contributions: List<GoalContributionEntity>)

    @Query("SELECT * FROM savings_goals")
    suspend fun allGoals(): List<SavingsGoalEntity>

    @Query("SELECT * FROM goal_contributions")
    suspend fun allContributions(): List<GoalContributionEntity>
}

@Dao
interface SubscriptionDao {
    @Upsert suspend fun upsert(subscription: SubscriptionEntity)
    @Upsert suspend fun upsertAll(subscriptions: List<SubscriptionEntity>)

    @Query("SELECT * FROM subscriptions WHERE deleted = 0 ORDER BY active DESC, nextPaymentDate")
    fun observeActive(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE deleted = 0 AND active = 1")
    suspend fun allActive(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE deleted = 0 AND active = 1 AND nextPaymentDate <= :nowMillis")
    suspend fun due(nowMillis: Long): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions")
    suspend fun all(): List<SubscriptionEntity>
}

@Dao
interface FavoriteDao {
    @Upsert suspend fun upsert(favorite: FavoriteEntity)
    @Upsert suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("SELECT * FROM favorites WHERE deleted = 0 ORDER BY position, title")
    fun observeActive(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE deleted = 0 ORDER BY position, title")
    suspend fun allActive(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE uid = :uid")
    suspend fun byUid(uid: String): FavoriteEntity?

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>
}

@Dao
interface DebtDao {
    @Upsert suspend fun upsert(debt: DebtEntity)
    @Upsert suspend fun upsertAll(debts: List<DebtEntity>)

    @Query("SELECT * FROM debts WHERE deleted = 0 ORDER BY date DESC")
    fun observeActive(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE deleted = 0 AND repaidCents < amountCents AND dueDate IS NOT NULL")
    suspend fun openWithDueDate(): List<DebtEntity>

    @Query("SELECT * FROM debts")
    suspend fun all(): List<DebtEntity>
}
