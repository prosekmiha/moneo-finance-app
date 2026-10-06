package si.moneo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity

class Converters {
    @TypeConverter fun transactionTypeToString(v: TransactionType) = v.name
    @TypeConverter fun stringToTransactionType(v: String) = TransactionType.valueOf(v)
    @TypeConverter fun sourceToString(v: TransactionSource) = v.name
    @TypeConverter fun stringToSource(v: String) = TransactionSource.valueOf(v)
    @TypeConverter fun frequencyToString(v: RecurrenceFrequency) = v.name
    @TypeConverter fun stringToFrequency(v: String) = RecurrenceFrequency.valueOf(v)
    @TypeConverter fun debtDirectionToString(v: DebtDirection) = v.name
    @TypeConverter fun stringToDebtDirection(v: String) = DebtDirection.valueOf(v)
}

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        RecurringRuleEntity::class,
        SavingsGoalEntity::class,
        GoalContributionEntity::class,
        SubscriptionEntity::class,
        FavoriteEntity::class,
        DebtEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun goalDao(): GoalDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun debtDao(): DebtDao

    companion object {
        /** v8: uporabnik izbere privzeti račun; obstoječim namestitvam je to "Glavni račun". */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE accounts SET isDefault = 1 WHERE uid = 'main' AND deleted = 0")
            }
        }

        /** v7: dan obračuna pri ponavljajočih pravilih. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_rules ADD COLUMN billingDay INTEGER")
            }
        }

        /** v6: samodejno mesečno vplačilo v varčevalni cilj. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE savings_goals ADD COLUMN monthlyAutoCents INTEGER")
                db.execSQL("ALTER TABLE savings_goals ADD COLUMN autoDay INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE savings_goals ADD COLUMN lastAutoMonth TEXT")
            }
        }

        /** v5: priljubljeni vnosi, dolgovi, oznake pri transakcijah. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorites (" +
                        "uid TEXT NOT NULL, title TEXT NOT NULL, type TEXT NOT NULL, amountCents INTEGER NOT NULL, " +
                        "categoryUid TEXT, accountUid TEXT, comment TEXT NOT NULL, position INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debts (" +
                        "uid TEXT NOT NULL, person TEXT NOT NULL, direction TEXT NOT NULL, amountCents INTEGER NOT NULL, " +
                        "repaidCents INTEGER NOT NULL, date INTEGER NOT NULL, dueDate INTEGER, note TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4: naročnine + povezava transakcije na naročnino. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS subscriptions (" +
                        "uid TEXT NOT NULL, title TEXT NOT NULL, amountCents INTEGER NOT NULL, currencyCode TEXT NOT NULL, " +
                        "frequency TEXT NOT NULL, `interval` INTEGER NOT NULL, nextPaymentDate INTEGER NOT NULL, " +
                        "billingDay INTEGER NOT NULL, startDate INTEGER, endDate INTEGER, categoryUid TEXT, accountUid TEXT, " +
                        "remindDaysBefore INTEGER, url TEXT NOT NULL, note TEXT NOT NULL, active INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN subscriptionUid TEXT")
            }
        }

        /** v3: začetno stanje računa + priponka (slika računa) pri transakciji. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN initialBalanceCents INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN attachmentPath TEXT")
            }
        }

        /** v2: varčevalni cilji + mesečni proračun na kategoriji. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudgetCents INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_goals (" +
                        "uid TEXT NOT NULL, title TEXT NOT NULL, emoji TEXT NOT NULL, targetCents INTEGER NOT NULL, " +
                        "deadline INTEGER, color INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS goal_contributions (" +
                        "uid TEXT NOT NULL, goalUid TEXT NOT NULL, amountCents INTEGER NOT NULL, date INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "deleted INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moje_finance.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
            }
    }
}
