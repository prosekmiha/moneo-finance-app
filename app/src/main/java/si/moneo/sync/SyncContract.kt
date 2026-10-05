package si.moneo.sync

import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransferEntity

/**
 * Pripravljenost za cloud sync (faza 2).
 *
 * Shema je že sync-ready:
 *  - uid (UUID) primarni ključi -> ni konfliktov med napravami
 *  - createdAt/updatedAt -> "last write wins" združevanje
 *  - deleted -> mehki brisi, ki se propagirajo
 *
 * Predlagan backend: Supabase (PostgreSQL + REST/Realtime) ali majhen lasten API.
 * Enaka tabela na strežniku kot lokalno + stolpec user_id (RLS).
 *
 * Potek sinhronizacije (enostaven "last write wins"):
 *  1. push: pošlji vse zapise z updatedAt > lastPushedAt (lokalni "dirty" kazalec)
 *  2. pull: povleci vse zapise z updatedAt > lastPulledAt in jih upsertaj lokalno
 *  3. konflikti: zmaga zapis z novejšim updatedAt (uid je isti na obeh straneh)
 *
 * Za sledenje lokalnim spremembam je dovolj, da vsak upsert nastavi updatedAt=now
 * (repozitorij to že dela), na strežniku pa hraniš lastPulledAt na napravo.
 */
interface SyncContract {

    data class Snapshot(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val transfers: List<TransferEntity>,
        val recurringRules: List<RecurringRuleEntity>,
        val subscriptions: List<SubscriptionEntity>,
        val favorites: List<FavoriteEntity>,
        val debts: List<DebtEntity>,
    )

    /** Potisne lokalne spremembe od [sinceMillis] naprej. Vrne nov "high watermark". */
    suspend fun push(sinceMillis: Long, snapshot: Snapshot): Long

    /** Povleče oddaljene spremembe od [sinceMillis] naprej. */
    suspend fun pull(sinceMillis: Long): Snapshot
}
