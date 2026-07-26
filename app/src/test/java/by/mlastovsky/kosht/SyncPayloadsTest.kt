package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.db.SyncMeta
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.UidRef
import by.mlastovsky.kosht.data.sync.SyncPayloads
import by.mlastovsky.kosht.data.sync.UidIndex
import by.mlastovsky.kosht.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The point of these: local row ids are per-device and must never travel, and
 * a device's own files must never be overwritten by what came off the wire.
 */
class SyncPayloadsTest {

    private val here = UidIndex(
        categories = listOf(UidRef(id = 7, uid = "seed:food")),
        accounts = listOf(UidRef(id = 3, uid = "acc-uid")),
        goals = emptyList()
    )

    /** Same account, second phone: same uids, entirely different local ids. */
    private val there = UidIndex(
        categories = listOf(UidRef(id = 41, uid = "seed:food")),
        accounts = listOf(UidRef(id = 92, uid = "acc-uid")),
        goals = emptyList()
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
        // The link itself is portable and does travel.
        assertEquals("https://echeck.example.by/r/8812ab", merged.receiptUrl)
        assertEquals(5L, merged.id)
        assertEquals(222L, merged.sync.updatedAt)
    }

    @Test
    fun `a row whose category has not arrived yet is not sent`() {
        val orphan = local.copy(categoryId = 999)
        assertNull(SyncPayloads.of(orphan, here))
    }
}
