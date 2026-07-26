package by.mlastovsky.kosht.data.sync

import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.SyncedSettings
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.AwardEntity
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RecurringEntity
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingGoalEntity
import by.mlastovsky.kosht.data.db.SyncEntity
import by.mlastovsky.kosht.data.db.SyncMeta
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.UidRef
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject

/** One record as the cloud stores it. */
data class SyncRow(
    val entity: SyncEntity,
    val uid: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val payload: JSONObject
)

/**
 * Translates between local row ids and the cloud identities they map to.
 * Local ids are per-device autoincrement values and mean nothing anywhere
 * else, so every reference travels as the target's uid.
 */
class UidIndex(
    categories: List<UidRef>,
    accounts: List<UidRef>,
    goals: List<UidRef>
) {
    private val categoryUidById = categories.associate { it.id to it.uid }
    private val categoryIdByUid = categories.associate { it.uid to it.id }
    private val accountUidById = accounts.associate { it.id to it.uid }
    private val accountIdByUid = accounts.associate { it.uid to it.id }
    private val goalUidById = goals.associate { it.id to it.uid }
    private val goalIdByUid = goals.associate { it.uid to it.id }

    fun categoryUid(id: Long): String? = categoryUidById[id]

    fun categoryId(uid: String?): Long? = uid?.let { categoryIdByUid[it] }

    fun accountUid(id: Long?): String? = id?.let { accountUidById[it] }

    fun accountId(uid: String?): Long? = uid?.let { accountIdByUid[it] }

    fun goalUid(id: Long?): String? = id?.let { goalUidById[it] }

    fun goalId(uid: String?): Long? = uid?.let { goalIdByUid[it] }
}

/**
 * Field names match the Room entities so the stored JSON stays readable, e.g.
 * `select payload->>'amountMinor' from sync_rows where entity='transactions'`.
 *
 * A photo's local path never travels -- it would be meaningless on another
 * device. The image itself stays on the phone unless the user switches photo
 * sync on, in which case the payload carries the name of the uploaded object
 * and the file goes to storage rather than into this row.
 */
object SyncPayloads {

    fun of(row: TransactionEntity, index: UidIndex): JSONObject? {
        val categoryUid = index.categoryUid(row.categoryId) ?: return null
        return JSONObject()
            .put("amountMinor", row.amountMinor)
            .put("type", row.type.name)
            .put("categoryUid", categoryUid)
            .put("note", row.note)
            .put("timestamp", row.timestamp)
            .put("createdAt", row.createdAt)
            .put("accountUid", index.accountUid(row.accountId))
            .put("bynMinor", row.bynMinor)
            .put("receiptUrl", row.receiptUrl)
            // Where the photo was uploaded, when the user asked for that. The
            // local path still never travels -- only the object's name does.
            .put("photoKey", row.photoKey)
    }

    fun toTransaction(
        json: JSONObject,
        meta: SyncMeta,
        index: UidIndex,
        local: TransactionEntity?
    ): TransactionEntity? {
        val categoryId = index.categoryId(json.stringOrNull("categoryUid")) ?: return null
        return TransactionEntity(
            id = local?.id ?: 0,
            amountMinor = json.getLong("amountMinor"),
            type = TransactionType.valueOf(json.getString("type")),
            categoryId = categoryId,
            note = json.optString("note"),
            timestamp = json.getLong("timestamp"),
            createdAt = json.getLong("createdAt"),
            // Local-only: the image itself never leaves the device.
            photoPath = local?.photoPath,
            accountId = index.accountId(json.stringOrNull("accountUid")),
            bynMinor = json.longOrNull("bynMinor"),
            receiptUrl = json.stringOrNull("receiptUrl"),
            // The link travels, the downloaded copy does not.
            receiptDocPath = local?.receiptDocPath,
            photoKey = json.stringOrNull("photoKey") ?: local?.photoKey,
            sync = meta
        )
    }

    fun of(row: CategoryEntity): JSONObject = JSONObject()
        .put("key", row.key)
        .put("name", row.name)
        .put("iconKey", row.iconKey)
        .put("colorArgb", row.colorArgb)
        .put("type", row.type.name)
        .put("position", row.position)

    fun toCategory(json: JSONObject, meta: SyncMeta, local: CategoryEntity?) = CategoryEntity(
        id = local?.id ?: 0,
        key = json.stringOrNull("key"),
        name = json.optString("name"),
        iconKey = json.optString("iconKey"),
        colorArgb = json.getLong("colorArgb"),
        type = TransactionType.valueOf(json.getString("type")),
        position = json.getInt("position"),
        sync = meta
    )

    fun of(row: AccountEntity): JSONObject = JSONObject()
        .put("key", row.key)
        .put("name", row.name)
        .put("iconKey", row.iconKey)
        .put("colorArgb", row.colorArgb)
        .put("position", row.position)
        .put("adjustmentMinor", row.adjustmentMinor)

    fun toAccount(json: JSONObject, meta: SyncMeta, local: AccountEntity?) = AccountEntity(
        id = local?.id ?: 0,
        key = json.stringOrNull("key"),
        name = json.optString("name"),
        iconKey = json.optString("iconKey"),
        colorArgb = json.getLong("colorArgb"),
        position = json.getInt("position"),
        adjustmentMinor = json.optLong("adjustmentMinor"),
        sync = meta
    )

    fun of(row: SavingGoalEntity): JSONObject = JSONObject()
        .put("title", row.title)
        .put("targetMinor", row.targetMinor)
        .put("currencyCode", row.currencyCode)
        .put("createdAt", row.createdAt)
        .put("achievedAt", row.achievedAt)

    fun toGoal(json: JSONObject, meta: SyncMeta, local: SavingGoalEntity?) = SavingGoalEntity(
        id = local?.id ?: 0,
        title = json.optString("title"),
        targetMinor = json.getLong("targetMinor"),
        currencyCode = json.optString("currencyCode"),
        createdAt = json.getLong("createdAt"),
        achievedAt = json.longOrNull("achievedAt"),
        sync = meta
    )

    fun of(row: SavingEntity, index: UidIndex): JSONObject = JSONObject()
        .put("amountMinor", row.amountMinor)
        .put("currencyCode", row.currencyCode)
        .put("note", row.note)
        .put("timestamp", row.timestamp)
        .put("goalUid", index.goalUid(row.goalId))

    fun toSaving(
        json: JSONObject,
        meta: SyncMeta,
        index: UidIndex,
        local: SavingEntity?
    ) = SavingEntity(
        id = local?.id ?: 0,
        amountMinor = json.getLong("amountMinor"),
        currencyCode = json.optString("currencyCode"),
        note = json.optString("note"),
        timestamp = json.getLong("timestamp"),
        goalId = index.goalId(json.stringOrNull("goalUid")),
        sync = meta
    )

    fun of(row: RecurringEntity, index: UidIndex): JSONObject? {
        val categoryUid = index.categoryUid(row.categoryId) ?: return null
        return JSONObject()
            .put("title", row.title)
            .put("amountMinor", row.amountMinor)
            .put("currencyCode", row.currencyCode)
            .put("categoryUid", categoryUid)
            .put("nextDueEpochDay", row.nextDueEpochDay)
            .put("frequency", row.frequency.name)
            .put("enabled", row.enabled)
            .put("createdAt", row.createdAt)
    }

    fun toRecurring(
        json: JSONObject,
        meta: SyncMeta,
        index: UidIndex,
        local: RecurringEntity?
    ): RecurringEntity? {
        val categoryId = index.categoryId(json.stringOrNull("categoryUid")) ?: return null
        return RecurringEntity(
            id = local?.id ?: 0,
            title = json.optString("title"),
            amountMinor = json.getLong("amountMinor"),
            currencyCode = json.optString("currencyCode"),
            categoryId = categoryId,
            nextDueEpochDay = json.getLong("nextDueEpochDay"),
            frequency = RecurringFrequency.valueOf(json.getString("frequency")),
            enabled = json.optBoolean("enabled", true),
            createdAt = json.getLong("createdAt"),
            sync = meta
        )
    }

    fun of(row: ChallengeEntity, index: UidIndex): JSONObject = JSONObject()
        .put("type", row.type.name)
        .put("title", row.title)
        .put("amountMinor", row.amountMinor)
        .put("categoryUid", row.categoryId?.let { index.categoryUid(it) })
        .put("startEpochDay", row.startEpochDay)
        .put("endEpochDay", row.endEpochDay)
        .put("createdAt", row.createdAt)

    fun toChallenge(
        json: JSONObject,
        meta: SyncMeta,
        index: UidIndex,
        local: ChallengeEntity?
    ) = ChallengeEntity(
        id = local?.id ?: 0,
        type = ChallengeType.valueOf(json.getString("type")),
        title = json.optString("title"),
        amountMinor = json.getLong("amountMinor"),
        categoryId = index.categoryId(json.stringOrNull("categoryUid")),
        startEpochDay = json.getLong("startEpochDay"),
        endEpochDay = json.getLong("endEpochDay"),
        createdAt = json.getLong("createdAt"),
        sync = meta
    )

    fun of(row: DebtEntity): JSONObject = JSONObject()
        .put("personName", row.personName)
        .put("direction", row.direction.name)
        .put("amountMinor", row.amountMinor)
        .put("currencyCode", row.currencyCode)
        .put("note", row.note)
        .put("createdAt", row.createdAt)
        .put("closedAt", row.closedAt)

    fun toDebt(json: JSONObject, meta: SyncMeta, local: DebtEntity?) = DebtEntity(
        id = local?.id ?: 0,
        personName = json.optString("personName"),
        direction = DebtDirection.valueOf(json.getString("direction")),
        amountMinor = json.getLong("amountMinor"),
        currencyCode = json.optString("currencyCode"),
        note = json.optString("note"),
        createdAt = json.getLong("createdAt"),
        closedAt = json.longOrNull("closedAt"),
        sync = meta
    )

    fun of(row: AwardEntity): JSONObject = JSONObject()
        .put("key", row.key)
        .put("unlockedAt", row.unlockedAt)

    fun toAward(json: JSONObject, meta: SyncMeta) = AwardEntity(
        key = json.getString("key"),
        unlockedAt = json.getLong("unlockedAt"),
        sync = meta
    )

    /**
     * The settings row. Unlike the others it has no local id and no uid of its
     * own — there is exactly one per account, under [SETTINGS_UID].
     */
    fun of(row: SyncedSettings): JSONObject = JSONObject()
        .put("currencyCode", row.settings.currencyCode)
        .put("themeMode", row.settings.themeMode.name)
        .put("dynamicColors", row.settings.dynamicColors)
        .put("notifyDailyReminder", row.settings.notifyDailyReminder)
        .put("notifyRecurringDue", row.settings.notifyRecurringDue)
        .put("notifyWeeklySummary", row.settings.notifyWeeklySummary)
        .put("notifyAwards", row.settings.notifyAwards)
        .put("dailyBudgetMinor", row.settings.dailyBudgetMinor)
        .put("showGreeting", row.settings.showGreeting)
        .put("showStreak", row.settings.showStreak)
        .put("showRates", row.settings.showRates)
        .put("convertOnCurrencyChange", row.settings.convertOnCurrencyChange)
        .put("multiAccount", row.settings.multiAccount)
        .put("reportFields", JSONArray(row.settings.reportFields.sorted()))
        .put("reportPeriod", row.settings.reportPeriod)
        .put("autoCalculator", row.settings.autoCalculator)
        .put("syncPhotos", row.settings.syncPhotos)
        .put("profileName", row.profileName)
        .put("profileNickname", row.profileNickname)
        .put("profileEmoji", row.profileEmoji)

    /**
     * [fallback] supplies anything an older or newer version of the app did
     * not send, so one unknown field never resets a screenful of preferences.
     */
    fun toSettings(
        json: JSONObject,
        updatedAt: Long,
        fallback: SyncedSettings
    ): SyncedSettings {
        val defaults = fallback.settings
        return SyncedSettings(
            updatedAt = updatedAt,
            settings = AppSettings(
                currencyCode = json.stringOrNull("currencyCode") ?: defaults.currencyCode,
                themeMode = json.stringOrNull("themeMode")
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: defaults.themeMode,
                dynamicColors = json.optBoolean("dynamicColors", defaults.dynamicColors),
                notifyDailyReminder = json.optBoolean(
                    "notifyDailyReminder",
                    defaults.notifyDailyReminder
                ),
                notifyRecurringDue = json.optBoolean(
                    "notifyRecurringDue",
                    defaults.notifyRecurringDue
                ),
                notifyWeeklySummary = json.optBoolean(
                    "notifyWeeklySummary",
                    defaults.notifyWeeklySummary
                ),
                notifyAwards = json.optBoolean("notifyAwards", defaults.notifyAwards),
                dailyBudgetMinor = json.longOrNull("dailyBudgetMinor")
                    ?: defaults.dailyBudgetMinor,
                showGreeting = json.optBoolean("showGreeting", defaults.showGreeting),
                showStreak = json.optBoolean("showStreak", defaults.showStreak),
                showRates = json.optBoolean("showRates", defaults.showRates),
                convertOnCurrencyChange = json.optBoolean(
                    "convertOnCurrencyChange",
                    defaults.convertOnCurrencyChange
                ),
                multiAccount = json.optBoolean("multiAccount", defaults.multiAccount),
                reportFields = json.optJSONArray("reportFields")
                    ?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                    ?: defaults.reportFields,
                reportPeriod = json.stringOrNull("reportPeriod") ?: defaults.reportPeriod,
                autoCalculator = json.optBoolean("autoCalculator", defaults.autoCalculator),
                syncPhotos = json.optBoolean("syncPhotos", defaults.syncPhotos)
            ),
            profileName = json.stringOrNull("profileName").orEmpty(),
            profileNickname = json.stringOrNull("profileNickname").orEmpty(),
            profileEmoji = json.stringOrNull("profileEmoji")
        )
    }

    /** One settings row per account, so its identity is a constant. */
    const val SETTINGS_UID = "settings"
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.longOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)
