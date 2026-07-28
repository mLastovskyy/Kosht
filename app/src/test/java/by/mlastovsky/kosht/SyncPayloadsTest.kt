package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.SyncedSettings
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.SyncMeta
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.TransactionItemEntity
import by.mlastovsky.kosht.data.db.UidRef
import by.mlastovsky.kosht.data.sync.SyncPayloads
import by.mlastovsky.kosht.data.sync.UidIndex
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadsTest {

    private val here = UidIndex(
        categories = listOf(UidRef(id = 7, uid = "seed:food")),
        accounts = listOf(UidRef(id = 3, uid = "acc-uid"), UidRef(id = 4, uid = "cash-uid")),
        goals = emptyList(),
        transactions = listOf(UidRef(id = 5, uid = "tx-uid"))
    )

    private val there = UidIndex(
        categories = listOf(UidRef(id = 41, uid = "seed:food")),
        accounts = listOf(UidRef(id = 92, uid = "acc-uid"), UidRef(id = 93, uid = "cash-uid")),
        goals = emptyList(),
        transactions = listOf(UidRef(id = 77, uid = "tx-uid"))
    )

    private val local = TransactionEntity(
        id = 5,
        amountMinor = 1250,
        type = TransactionType.EXPENSE,
        categoryId = 7,
        note = "Хлеб",
        timestamp = 1_700_000_000_000,
        createdAt = 1_700_000_000_000,
        photoPath = "/data/user/0/kosht/files/receipts/photo_1.jpg",
        accountId = 3,
        bynMinor = 1250,
        receiptUrl = "https://echeck.example.by/r/8812ab",
        receiptDocPath = "/data/user/0/kosht/files/receipts/echeck_1.html",
        sync = SyncMeta(uid = "tx-uid", updatedAt = 111)
    )

    @Test
    fun `foreign keys travel as uids and land on the other device's ids`() {
        val payload = SyncPayloads.of(local, here)!!
        assertEquals("seed:food", payload.getString("categoryUid"))
        assertEquals("acc-uid", payload.getString("accountUid"))

        val arrived = SyncPayloads.toTransaction(
            json = payload,
            meta = SyncMeta(uid = "tx-uid", updatedAt = 111),
            index = there,
            local = null
        )!!
        assertEquals(41L, arrived.categoryId)
        assertEquals(92L, arrived.accountId)
        assertEquals(1250L, arrived.amountMinor)
        assertEquals("Хлеб", arrived.note)
        // A brand-new row here, so Room assigns the id.
        assertEquals(0L, arrived.id)
    }

    @Test
    fun `a saving challenge lands on the other device's goal`() {
        val mine = UidIndex(
            categories = emptyList(),
            accounts = emptyList(),
            goals = listOf(UidRef(id = 12, uid = "goal-uid"))
        )
        val yours = UidIndex(
            categories = emptyList(),
            accounts = emptyList(),
            goals = listOf(UidRef(id = 64, uid = "goal-uid"))
        )
        val challenge = ChallengeEntity(
            id = 2,
            type = ChallengeType.SAVE_TARGET,
            title = "На отпуск",
            amountMinor = 500_00,
            currencyCode = "USD",
            goalId = 12,
            startEpochDay = 20_600,
            endEpochDay = 20_630,
            createdAt = 1_700_000_000_000,
            sync = SyncMeta(uid = "ch-uid", updatedAt = 5)
        )

        val payload = SyncPayloads.of(challenge, mine)
        assertEquals("goal-uid", payload.getString("goalUid"))
        assertEquals("USD", payload.getString("currencyCode"))

        val arrived = SyncPayloads.toChallenge(
            json = payload,
            meta = SyncMeta(uid = "ch-uid", updatedAt = 5),
            index = yours,
            local = null
        )
        assertEquals(64L, arrived.goalId)
        assertEquals("USD", arrived.currencyCode)
        assertEquals(500_00L, arrived.amountMinor)
    }

    @Test
    fun `a challenge from an older version keeps the app currency`() {
        val plain = UidIndex(categories = emptyList(), accounts = emptyList(), goals = emptyList())
        val payload = SyncPayloads.of(
            ChallengeEntity(
                type = ChallengeType.NO_SPEND,
                title = "Неделя без трат",
                amountMinor = 0,
                startEpochDay = 20_600,
                endEpochDay = 20_606,
                createdAt = 1_700_000_000_000
            ),
            plain
        )
        val arrived = SyncPayloads.toChallenge(
            json = payload,
            meta = SyncMeta(uid = "ch-uid", updatedAt = 5),
            index = plain,
            local = null
        )
        assertNull(arrived.currencyCode)
        assertNull(arrived.goalId)
    }

    @Test
    fun `device-local attachments survive an incoming update`() {
        val payload = SyncPayloads.of(local, here)!!
        val existing = local.copy(
            photoPath = "/data/user/0/kosht/files/receipts/mine.jpg",
            receiptDocPath = "/data/user/0/kosht/files/receipts/mine.html"
        )

        val merged = SyncPayloads.toTransaction(
            json = payload,
            meta = SyncMeta(uid = "tx-uid", updatedAt = 222),
            index = here,
            local = existing
        )!!

        // Paths point at files on this phone; the other phone's are useless.
        assertEquals("/data/user/0/kosht/files/receipts/mine.jpg", merged.photoPath)
        assertEquals("/data/user/0/kosht/files/receipts/mine.html", merged.receiptDocPath)

        assertEquals("https://echeck.example.by/r/8812ab", merged.receiptUrl)
        assertEquals(5L, merged.id)
        assertEquals(222L, merged.sync.updatedAt)
    }

    @Test
    fun `a row whose category has not arrived yet is not sent`() {
        val orphan = local.copy(categoryId = 999)
        assertNull(SyncPayloads.of(orphan, here))
    }

    @Test
    fun `both ends of a transfer travel as uids`() {
        val transfer = local.copy(
            transferToAccountId = 4,
            transferFeeMinor = 150,
            scanned = false
        )
        val payload = SyncPayloads.of(transfer, here)!!
        assertEquals("cash-uid", payload.getString("transferToAccountUid"))

        val arrived = SyncPayloads.toTransaction(payload, SyncMeta("tx-uid", 111), there, null)!!
        assertEquals(93L, arrived.transferToAccountId)
        assertEquals(150L, arrived.transferFeeMinor)
        assertEquals(92L, arrived.accountId)
    }

    @Test
    fun `the scanner mark travels with the record`() {
        val payload = SyncPayloads.of(local.copy(scanned = true), here)!!
        val arrived = SyncPayloads.toTransaction(payload, SyncMeta("tx-uid", 111), there, null)!!
        assertTrue(arrived.scanned)
    }

    @Test
    fun `a product line lands on the other device's record`() {
        val line = TransactionItemEntity(
            id = 12,
            transactionId = 5,
            name = "Молоко",
            amountMinor = 245,
            quantity = 2.0,
            position = 1,
            sync = SyncMeta(uid = "item-uid", updatedAt = 222)
        )
        val payload = SyncPayloads.of(line, here)!!
        assertEquals("tx-uid", payload.getString("transactionUid"))

        val arrived = SyncPayloads.toItem(payload, SyncMeta("item-uid", 222), there, null)!!
        assertEquals(77L, arrived.transactionId)
        assertEquals("Молоко", arrived.name)
        assertEquals(245L, arrived.amountMinor)
        assertEquals(2.0, arrived.quantity!!, 0.001)
        assertEquals(1, arrived.position)
    }

    @Test
    fun `a product line whose record has not arrived yet waits`() {
        val line = TransactionItemEntity(
            transactionId = 999,
            name = "Хлеб",
            sync = SyncMeta(uid = "item-uid", updatedAt = 1)
        )
        assertNull(SyncPayloads.of(line, here))
    }

    // ---- Settings ---------------------------------------------------------

    private val defaults = AppSettings(
        currencyCode = "BYN",
        themeMode = ThemeMode.SYSTEM,
        dynamicColors = false,
        notifyDailyReminder = false,
        notifyRecurringDue = true,
        notifyWeeklySummary = false,
        notifyAwards = true,
        dailyBudgetMinor = 0,
        showGreeting = true,
        showStreak = true,
        showRates = true,
        convertOnCurrencyChange = true,
        multiAccount = false,
        transferFee = false,
        reportFields = setOf("SPENT", "INCOME"),
        reportPeriod = "MONTH",
        autoCalculator = true,
        syncPhotos = false
    )

    private val untouched = SyncedSettings(
        updatedAt = 0,
        settings = defaults,
        profileNickname = "",
        profileEmoji = null
    )

    @Test
    fun `settings survive the round trip to the other phone`() {
        val mine = SyncedSettings(
            updatedAt = 500,
            settings = defaults.copy(
                currencyCode = "USD",
                themeMode = ThemeMode.DARK,
                dynamicColors = true,
                notifyRecurringDue = false,
                dailyBudgetMinor = 4500,
                showGreeting = false,
                multiAccount = true,
                reportFields = setOf("NET", "AVG_DAY", "FREE_DAYS"),
                reportPeriod = "WEEK",
                autoCalculator = false,
                syncPhotos = true
            ),
            profileNickname = "mLastovskyy",
            profileEmoji = "emoji:🦊"
        )

        val arrived = SyncPayloads.toSettings(SyncPayloads.of(mine), 500, untouched)

        assertEquals(mine.settings, arrived.settings)
        assertEquals("mLastovskyy", arrived.profileNickname)
        assertEquals("emoji:🦊", arrived.profileEmoji)
        assertEquals(500L, arrived.updatedAt)
    }

    @Test
    fun `a name from an older build arrives as the nickname`() {
        val payload = SyncPayloads.of(untouched)
        payload.remove("profileNickname")
        payload.put("profileName", "Максим")

        val arrived = SyncPayloads.toSettings(payload, 500, untouched)

        assertEquals("Максим", arrived.profileNickname)
    }

    @Test
    fun `a field the other version never sent keeps its local value`() {
        val partial = SyncPayloads.of(untouched.copy(settings = defaults))
        partial.remove("notifyAwards")
        partial.remove("reportPeriod")

        val mine = untouched.copy(
            settings = defaults.copy(notifyAwards = false, reportPeriod = "YEAR")
        )
        val arrived = SyncPayloads.toSettings(partial, 900, mine)

        // Missing is not the same as "switch it off".
        assertFalse(arrived.settings.notifyAwards)
        assertEquals("YEAR", arrived.settings.reportPeriod)
    }

    @Test
    fun `a photo avatar is not offered to the other device`() {
        val photo = untouched.copy(profileEmoji = null)
        assertTrue(SyncPayloads.of(photo).isNull("profileEmoji"))
    }
}
