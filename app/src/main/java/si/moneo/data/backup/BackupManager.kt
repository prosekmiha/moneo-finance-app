package si.moneo.data.backup

import si.moneo.R
import si.moneo.ui.str
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import si.moneo.data.db.AppDatabase
import si.moneo.data.db.entity.AccountEntity
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.DebtDirection
import si.moneo.data.db.entity.DebtEntity
import si.moneo.data.db.entity.FavoriteEntity
import si.moneo.data.prefs.AppPrefs
import si.moneo.data.db.entity.GoalContributionEntity
import si.moneo.data.db.entity.RecurrenceFrequency
import si.moneo.data.db.entity.RecurringRuleEntity
import si.moneo.data.db.entity.SubscriptionEntity
import si.moneo.data.db.entity.SavingsGoalEntity
import si.moneo.data.db.entity.TransactionEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.data.db.entity.TransferEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Varnostna kopija vseh podatkov v JSON (vključno z izbrisanimi zapisi - soft delete - da se
 * brisanja pravilno prenesejo) in izvoz transakcij v CSV za Excel.
 *
 * Obnovitev združuje po uid: zmaga zapis z novejšim updatedAt, zato je ponovna obnovitev varna.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    data class RestoreResult(val inserted: Int, val updated: Int, val skipped: Int)

    // ---------------- JSON ----------------

    suspend fun exportJson(): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("accounts", JSONArray(db.accountDao().all().map { it.toJson() }))
            .put("categories", JSONArray(db.categoryDao().all().map { it.toJson() }))
            .put("transactions", JSONArray(db.transactionDao().all().map { it.toJson() }))
            .put("transfers", JSONArray(db.transferDao().all().map { it.toJson() }))
            .put("recurringRules", JSONArray(db.recurringRuleDao().all().map { it.toJson() }))
            .put("goals", JSONArray(db.goalDao().allGoals().map { it.toJson() }))
            .put("goalContributions", JSONArray(db.goalDao().allContributions().map { it.toJson() }))
            .put("subscriptions", JSONArray(db.subscriptionDao().all().map { it.toJson() }))
            .put("favorites", JSONArray(db.favoriteDao().all().map { it.toJson() }))
            .put("debts", JSONArray(db.debtDao().all().map { it.toJson() }))
            .put("settings", JSONObject().put("monthlyBudgetCents", AppPrefs(context).monthlyBudgetCents.orNull()))
        return root.toString()
    }

    suspend fun writeJson(uri: Uri) {
        val json = exportJson()
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun restoreJson(uri: Uri): RestoreResult {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { str(R.string.not_a_backup) }

        var inserted = 0; var updated = 0; var skipped = 0
        /** Vrne zapise, ki so novejši od obstoječih (ali novi). */
        fun <T> merge(incoming: List<T>, existing: List<T>, uid: (T) -> String, updatedAt: (T) -> Long): List<T> {
            val byUid = existing.associateBy(uid)
            return incoming.filter { item ->
                val old = byUid[uid(item)]
                when {
                    old == null -> { inserted++; true }
                    updatedAt(item) > updatedAt(old) -> { updated++; true }
                    else -> { skipped++; false }
                }
            }
        }

        db.withTransaction {
            db.accountDao().upsertAll(merge(root.array("accounts") { accountFrom(it) }, db.accountDao().all(), { it.uid }, { it.updatedAt }))
            db.categoryDao().upsertAll(merge(root.array("categories") { categoryFrom(it) }, db.categoryDao().all(), { it.uid }, { it.updatedAt }))
            val localTxs = db.transactionDao().all()
            val localAttachments = localTxs.associate { it.uid to it.attachmentPath }
            db.transactionDao().upsertAll(
                merge(root.array("transactions") { transactionFrom(it) }, localTxs, { it.uid }, { it.updatedAt })
                    // slika računa je le lokalno - ohrani jo, tudi če je zapis iz kopije novejši
                    .map { it.copy(attachmentPath = localAttachments[it.uid]) },
            )
            db.transferDao().upsertAll(merge(root.array("transfers") { transferFrom(it) }, db.transferDao().all(), { it.uid }, { it.updatedAt }))
            db.recurringRuleDao().upsertAll(merge(root.array("recurringRules") { ruleFrom(it) }, db.recurringRuleDao().all(), { it.uid }, { it.updatedAt }))
            db.goalDao().upsertGoals(merge(root.array("goals") { goalFrom(it) }, db.goalDao().allGoals(), { it.uid }, { it.updatedAt }))
            db.goalDao().upsertContributions(merge(root.array("goalContributions") { contributionFrom(it) }, db.goalDao().allContributions(), { it.uid }, { it.updatedAt }))
            db.subscriptionDao().upsertAll(merge(root.array("subscriptions") { subscriptionFrom(it) }, db.subscriptionDao().all(), { it.uid }, { it.updatedAt }))
            db.favoriteDao().upsertAll(merge(root.array("favorites") { favoriteFrom(it) }, db.favoriteDao().all(), { it.uid }, { it.updatedAt }))
            db.debtDao().upsertAll(merge(root.array("debts") { debtFrom(it) }, db.debtDao().all(), { it.uid }, { it.updatedAt }))
        }
        // Skupni proračun iz kopije le, če lokalno še ni nastavljen
        root.optJSONObject("settings")?.optNullableLong("monthlyBudgetCents")?.let { budget ->
            val prefs = AppPrefs(context)
            if (prefs.monthlyBudgetCents == null) prefs.monthlyBudgetCents = budget
        }
        return RestoreResult(inserted, updated, skipped)
    }

    /** Samodejna kopija v mapo, ki jo je uporabnik izbral (SAF tree URI). Vrne ime datoteke. */
    suspend fun writeToFolder(treeUri: Uri): String {
        val name = "moneo-${LocalDate.now()}.json"
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val file = DocumentsContract.createDocument(context.contentResolver, parent, "application/json", name)
            ?: error(str(R.string.cannot_create_file))
        writeJson(file)
        return name
    }

    // ---------------- CSV ----------------

    /** Transakcije v CSV s podpičjem in decimalno vejico (slovenski Excel), UTF-8 z BOM za šumnike. */
    suspend fun writeCsv(uri: Uri): Int {
        val categories = db.categoryDao().all().associateBy { it.uid }
        val accounts = db.accountDao().all().associateBy { it.uid }
        val txs = db.transactionDao().all().filter { !it.deleted }.sortedByDescending { it.date }
        val zone = ZoneId.systemDefault()
        val sb = StringBuilder("﻿")
        sb.append(str(R.string.csv_header)).append("\r\n")
        val decimal = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
        txs.forEach { tx ->
            val date = Instant.ofEpochMilli(tx.date).atZone(zone).toLocalDate()
            val signed = if (tx.type == TransactionType.EXPENSE) -tx.amountCents else tx.amountCents
            val amount = "%s%d%s%02d".format(if (signed < 0) "-" else "", kotlin.math.abs(signed) / 100, decimal, kotlin.math.abs(signed) % 100)
            sb.append(
                listOf(
                    "${date.dayOfMonth}.${date.monthValue}.${date.year}",
                    str(if (tx.type == TransactionType.EXPENSE) R.string.entry_expense else R.string.entry_income),
                    amount,
                    categories[tx.categoryUid]?.title.orEmpty(),
                    accounts[tx.accountUid]?.title.orEmpty(),
                    tx.comment,
                    tx.tags,
                    tx.source.name.lowercase(),
                    str(if (tx.confirmed) R.string.yes else R.string.no),
                ).joinToString(";") { csvEscape(it) },
            ).append("\r\n")
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return txs.size
    }

    private fun csvEscape(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    // ---------------- mapiranje ----------------

    private fun <T> JSONObject.array(key: String, map: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
    }

    private fun JSONObject.optNullableString(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
    private fun JSONObject.optNullableLong(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optNullableInt(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    private fun AccountEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("currencyCode", currencyCode).put("icon", icon.orNull())
        .put("color", color.orNull()).put("position", position).put("isActive", isActive)
        .put("initialBalanceCents", initialBalanceCents)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun accountFrom(o: JSONObject) = AccountEntity(
        uid = o.getString("uid"), title = o.getString("title"), currencyCode = o.optString("currencyCode", "EUR"),
        icon = o.optNullableString("icon"), color = o.optNullableInt("color"), position = o.optInt("position"),
        isActive = o.optBoolean("isActive", true), initialBalanceCents = o.optLong("initialBalanceCents", 0),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun CategoryEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("type", type.name).put("icon", icon.orNull()).put("color", color.orNull())
        .put("position", position).put("keywords", keywords).put("monthlyBudgetCents", monthlyBudgetCents.orNull())
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun categoryFrom(o: JSONObject) = CategoryEntity(
        uid = o.getString("uid"), title = o.getString("title"), type = TransactionType.valueOf(o.getString("type")),
        icon = o.optNullableString("icon"), color = o.optNullableInt("color"), position = o.optInt("position"),
        keywords = o.optString("keywords"), monthlyBudgetCents = o.optNullableLong("monthlyBudgetCents"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun TransactionEntity.toJson() = JSONObject()
        .put("uid", uid).put("type", type.name).put("amountCents", amountCents).put("currencyCode", currencyCode)
        .put("date", date).put("comment", comment).put("categoryUid", categoryUid.orNull()).put("accountUid", accountUid.orNull())
        .put("source", source.name).put("confirmed", confirmed).put("recurringRuleUid", recurringRuleUid.orNull())
        .put("subscriptionUid", subscriptionUid.orNull()).put("tags", tags)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)
    // attachmentPath namerno ni v kopiji - slika je le na tej napravi

    private fun transactionFrom(o: JSONObject) = TransactionEntity(
        uid = o.getString("uid"), type = TransactionType.valueOf(o.getString("type")), amountCents = o.getLong("amountCents"),
        currencyCode = o.optString("currencyCode", "EUR"), date = o.getLong("date"), comment = o.optString("comment"),
        categoryUid = o.optNullableString("categoryUid"), accountUid = o.optNullableString("accountUid"),
        source = runCatching { TransactionSource.valueOf(o.getString("source")) }.getOrDefault(TransactionSource.IMPORT),
        confirmed = o.optBoolean("confirmed", true), recurringRuleUid = o.optNullableString("recurringRuleUid"),
        subscriptionUid = o.optNullableString("subscriptionUid"), tags = o.optString("tags"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun TransferEntity.toJson() = JSONObject()
        .put("uid", uid).put("fromAccountUid", fromAccountUid.orNull()).put("toAccountUid", toAccountUid.orNull())
        .put("fromAmountCents", fromAmountCents).put("toAmountCents", toAmountCents.orNull()).put("currencyCode", currencyCode)
        .put("date", date).put("comment", comment)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun transferFrom(o: JSONObject) = TransferEntity(
        uid = o.getString("uid"), fromAccountUid = o.optNullableString("fromAccountUid"), toAccountUid = o.optNullableString("toAccountUid"),
        fromAmountCents = o.getLong("fromAmountCents"), toAmountCents = o.optNullableLong("toAmountCents"),
        currencyCode = o.optString("currencyCode", "EUR"), date = o.getLong("date"), comment = o.optString("comment"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun RecurringRuleEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("type", type.name).put("amountCents", amountCents)
        .put("categoryUid", categoryUid.orNull()).put("accountUid", accountUid.orNull()).put("frequency", frequency.name)
        .put("interval", interval).put("nextDueDate", nextDueDate).put("endDate", endDate.orNull())
        .put("billingDay", billingDay.orNull())
        .put("autoAdd", autoAdd).put("enabled", enabled)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun ruleFrom(o: JSONObject) = RecurringRuleEntity(
        uid = o.getString("uid"), title = o.getString("title"), type = TransactionType.valueOf(o.getString("type")),
        amountCents = o.getLong("amountCents"), categoryUid = o.optNullableString("categoryUid"),
        accountUid = o.optNullableString("accountUid"), frequency = RecurrenceFrequency.valueOf(o.getString("frequency")),
        interval = o.optInt("interval", 1), nextDueDate = o.getLong("nextDueDate"), endDate = o.optNullableLong("endDate"),
        billingDay = o.optNullableInt("billingDay"),
        autoAdd = o.optBoolean("autoAdd"), enabled = o.optBoolean("enabled", true),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun SubscriptionEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("amountCents", amountCents).put("currencyCode", currencyCode)
        .put("frequency", frequency.name).put("interval", interval).put("nextPaymentDate", nextPaymentDate)
        .put("billingDay", billingDay).put("startDate", startDate.orNull()).put("endDate", endDate.orNull())
        .put("categoryUid", categoryUid.orNull()).put("accountUid", accountUid.orNull())
        .put("remindDaysBefore", remindDaysBefore.orNull()).put("url", url).put("note", note).put("active", active)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun subscriptionFrom(o: JSONObject) = SubscriptionEntity(
        uid = o.getString("uid"), title = o.getString("title"), amountCents = o.getLong("amountCents"),
        currencyCode = o.optString("currencyCode", "EUR"), frequency = RecurrenceFrequency.valueOf(o.getString("frequency")),
        interval = o.optInt("interval", 1), nextPaymentDate = o.getLong("nextPaymentDate"), billingDay = o.optInt("billingDay", 1),
        startDate = o.optNullableLong("startDate"), endDate = o.optNullableLong("endDate"),
        categoryUid = o.optNullableString("categoryUid"), accountUid = o.optNullableString("accountUid"),
        remindDaysBefore = o.optNullableInt("remindDaysBefore"), url = o.optString("url"), note = o.optString("note"),
        active = o.optBoolean("active", true),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun FavoriteEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("type", type.name).put("amountCents", amountCents)
        .put("categoryUid", categoryUid.orNull()).put("accountUid", accountUid.orNull()).put("comment", comment)
        .put("position", position).put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun favoriteFrom(o: JSONObject) = FavoriteEntity(
        uid = o.getString("uid"), title = o.getString("title"), type = TransactionType.valueOf(o.getString("type")),
        amountCents = o.getLong("amountCents"), categoryUid = o.optNullableString("categoryUid"),
        accountUid = o.optNullableString("accountUid"), comment = o.optString("comment"), position = o.optInt("position"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun DebtEntity.toJson() = JSONObject()
        .put("uid", uid).put("person", person).put("direction", direction.name).put("amountCents", amountCents)
        .put("repaidCents", repaidCents).put("date", date).put("dueDate", dueDate.orNull()).put("note", note)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun debtFrom(o: JSONObject) = DebtEntity(
        uid = o.getString("uid"), person = o.getString("person"), direction = DebtDirection.valueOf(o.getString("direction")),
        amountCents = o.getLong("amountCents"), repaidCents = o.optLong("repaidCents"), date = o.getLong("date"),
        dueDate = o.optNullableLong("dueDate"), note = o.optString("note"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun SavingsGoalEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("emoji", emoji).put("targetCents", targetCents)
        .put("deadline", deadline.orNull()).put("color", color.orNull())
        .put("monthlyAutoCents", monthlyAutoCents.orNull()).put("autoDay", autoDay).put("lastAutoMonth", lastAutoMonth.orNull())
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun goalFrom(o: JSONObject) = SavingsGoalEntity(
        uid = o.getString("uid"), title = o.getString("title"), emoji = o.optString("emoji", "🎯"),
        targetCents = o.getLong("targetCents"), deadline = o.optNullableLong("deadline"), color = o.optNullableInt("color"),
        monthlyAutoCents = o.optNullableLong("monthlyAutoCents"), autoDay = o.optInt("autoDay", 1),
        lastAutoMonth = o.optNullableString("lastAutoMonth"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    private fun GoalContributionEntity.toJson() = JSONObject()
        .put("uid", uid).put("goalUid", goalUid).put("amountCents", amountCents).put("date", date).put("note", note)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun contributionFrom(o: JSONObject) = GoalContributionEntity(
        uid = o.getString("uid"), goalUid = o.getString("goalUid"), amountCents = o.getLong("amountCents"),
        date = o.getLong("date"), note = o.optString("note"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.optBoolean("deleted"),
    )

    companion object {
        const val FORMAT = "moje-finance-backup"
        const val VERSION = 1
    }
}
