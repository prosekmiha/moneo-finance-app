package si.moneo.data.backup

import si.moneo.R
import si.moneo.ui.str
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Varnostna kopija vseh podatkov v JSON (vključno z izbrisanimi zapisi - soft delete - da se
 * brisanja pravilno prenesejo), izvoz celotne baze v SQLite in izvoz transakcij v CSV za Excel.
 *
 * Obnovitev (iz JSON ali SQLite) združuje po uid: zmaga zapis z novejšim updatedAt, zato je
 * ponovna obnovitev varna.
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

    /** Obnovi iz JSON kopije ali SQLite izvoza - format se prepozna po vsebini datoteke. */
    suspend fun restore(uri: Uri): RestoreResult {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return if (bytes.size >= SQLITE_HEADER.size && bytes.copyOf(SQLITE_HEADER.size).contentEquals(SQLITE_HEADER)) {
            restoreJson(readSqlite(bytes))
        } else {
            restoreJson(JSONObject(bytes.toString(Charsets.UTF_8)))
        }
    }

    private suspend fun restoreJson(root: JSONObject): RestoreResult {
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

    // ---------------- SQLite ----------------

    /**
     * Samostojna SQLite datoteka z vsemi tabelami in indeksi (za DB Browser, Python ...), ki jo zna
     * [restore] tudi uvoziti. Prebere en posnetek baze v transakciji. Vrne število vrstic.
     */
    suspend fun writeSqlite(uri: Uri): Int {
        val file = File(context.cacheDir, "moneo-export.db")
        SQLiteDatabase.deleteDatabase(file)
        var rows = 0
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { out ->
                val src = db.openHelper.writableDatabase
                db.withTransaction {
                    out.beginTransaction()
                    try {
                        val schema = src.query(
                            "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL " +
                                "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY type DESC",
                        ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) } }
                        // najprej tabele (z vrsticami), nato indeksi
                        schema.forEach { (type, name, sql) ->
                            out.execSQL(sql)
                            if (type != "table") return@forEach
                            src.query("SELECT * FROM `$name`").use { c ->
                                while (c.moveToNext()) {
                                    out.insertOrThrow(name, null, c.toContentValues())
                                    rows++
                                }
                            }
                        }
                        out.version = src.version
                        out.setTransactionSuccessful()
                    } finally {
                        out.endTransaction()
                    }
                }
            }
            context.contentResolver.openOutputStream(uri, "wt")!!.use { os -> file.inputStream().use { it.copyTo(os) } }
        } finally {
            SQLiteDatabase.deleteDatabase(file)
        }
        return rows
    }

    /** Prebere SQLite izvoz v enako JSON obliko kot [exportJson], da gre skozi isto združevanje. */
    private fun readSqlite(bytes: ByteArray): JSONObject {
        val file = File(context.cacheDir, "moneo-import.db")
        SQLiteDatabase.deleteDatabase(file)
        file.writeBytes(bytes)
        try {
            return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { src ->
                val tables = src.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)
                    .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
                require("transactions" in tables && "accounts" in tables) { str(R.string.not_a_backup) }
                JSONObject().put("format", FORMAT).apply {
                    SQLITE_TABLES.forEach { (table, key) -> if (table in tables) put(key, src.rows(table)) }
                }
            }
        } finally {
            SQLiteDatabase.deleteDatabase(file)
        }
    }

    private fun SQLiteDatabase.rows(table: String): JSONArray = rawQuery("SELECT * FROM `$table`", null).use { c ->
        JSONArray().apply {
            while (c.moveToNext()) {
                put(JSONObject().apply {
                    for (i in 0 until c.columnCount) {
                        put(c.getColumnName(i), when (c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> c.getString(i)
                            else -> JSONObject.NULL
                        })
                    }
                })
            }
        }
    }

    private fun Cursor.toContentValues() = ContentValues(columnCount).also { cv ->
        for (i in 0 until columnCount) {
            val col = getColumnName(i)
            when (getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> cv.put(col, getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> cv.put(col, getDouble(i))
                Cursor.FIELD_TYPE_STRING -> cv.put(col, getString(i))
                Cursor.FIELD_TYPE_BLOB -> cv.put(col, getBlob(i))
                else -> cv.putNull(col)
            }
        }
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
    /** JSON kopija ima true/false, SQLite pa 0/1. */
    private fun JSONObject.bool(k: String, default: Boolean = false): Boolean = when (val v = opt(k)) {
        is Boolean -> v
        is Number -> v.toLong() != 0L
        else -> default
    }

    private fun AccountEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("currencyCode", currencyCode).put("icon", icon.orNull())
        .put("color", color.orNull()).put("position", position).put("isActive", isActive)
        .put("initialBalanceCents", initialBalanceCents)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted).put("isDefault", isDefault)

    private fun accountFrom(o: JSONObject) = AccountEntity(
        uid = o.getString("uid"), title = o.getString("title"), currencyCode = o.optString("currencyCode", "EUR"),
        icon = o.optNullableString("icon"), color = o.optNullableInt("color"), position = o.optInt("position"),
        isActive = o.bool("isActive", true), initialBalanceCents = o.optLong("initialBalanceCents", 0),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
        isDefault = o.bool("isDefault"),
    )

    private fun CategoryEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("type", type.name).put("icon", icon.orNull()).put("color", color.orNull())
        .put("position", position).put("keywords", keywords).put("monthlyBudgetCents", monthlyBudgetCents.orNull())
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun categoryFrom(o: JSONObject) = CategoryEntity(
        uid = o.getString("uid"), title = o.getString("title"), type = TransactionType.valueOf(o.getString("type")),
        icon = o.optNullableString("icon"), color = o.optNullableInt("color"), position = o.optInt("position"),
        keywords = o.optString("keywords"), monthlyBudgetCents = o.optNullableLong("monthlyBudgetCents"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
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
        confirmed = o.bool("confirmed", true), recurringRuleUid = o.optNullableString("recurringRuleUid"),
        subscriptionUid = o.optNullableString("subscriptionUid"), tags = o.optString("tags"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
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
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
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
        autoAdd = o.bool("autoAdd"), enabled = o.bool("enabled", true),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
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
        active = o.bool("active", true),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
    )

    private fun FavoriteEntity.toJson() = JSONObject()
        .put("uid", uid).put("title", title).put("type", type.name).put("amountCents", amountCents)
        .put("categoryUid", categoryUid.orNull()).put("accountUid", accountUid.orNull()).put("comment", comment)
        .put("position", position).put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun favoriteFrom(o: JSONObject) = FavoriteEntity(
        uid = o.getString("uid"), title = o.getString("title"), type = TransactionType.valueOf(o.getString("type")),
        amountCents = o.getLong("amountCents"), categoryUid = o.optNullableString("categoryUid"),
        accountUid = o.optNullableString("accountUid"), comment = o.optString("comment"), position = o.optInt("position"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
    )

    private fun DebtEntity.toJson() = JSONObject()
        .put("uid", uid).put("person", person).put("direction", direction.name).put("amountCents", amountCents)
        .put("repaidCents", repaidCents).put("date", date).put("dueDate", dueDate.orNull()).put("note", note)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun debtFrom(o: JSONObject) = DebtEntity(
        uid = o.getString("uid"), person = o.getString("person"), direction = DebtDirection.valueOf(o.getString("direction")),
        amountCents = o.getLong("amountCents"), repaidCents = o.optLong("repaidCents"), date = o.getLong("date"),
        dueDate = o.optNullableLong("dueDate"), note = o.optString("note"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
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
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
    )

    private fun GoalContributionEntity.toJson() = JSONObject()
        .put("uid", uid).put("goalUid", goalUid).put("amountCents", amountCents).put("date", date).put("note", note)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deleted", deleted)

    private fun contributionFrom(o: JSONObject) = GoalContributionEntity(
        uid = o.getString("uid"), goalUid = o.getString("goalUid"), amountCents = o.getLong("amountCents"),
        date = o.getLong("date"), note = o.optString("note"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deleted = o.bool("deleted"),
    )

    companion object {
        const val FORMAT = "moje-finance-backup"
        const val VERSION = 1

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /** Tabela v bazi -> ključ v JSON kopiji. */
        private val SQLITE_TABLES = listOf(
            "accounts" to "accounts",
            "categories" to "categories",
            "transactions" to "transactions",
            "transfers" to "transfers",
            "recurring_rules" to "recurringRules",
            "savings_goals" to "goals",
            "goal_contributions" to "goalContributions",
            "subscriptions" to "subscriptions",
            "favorites" to "favorites",
            "debts" to "debts",
        )
    }
}
