package by.mlastovsky.kosht

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.db.SyncMeta
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtRecordTest {

    private lateinit var db: KoshtDatabase
    private lateinit var repository: TransactionRepository

    private var debtCategoryId = 0L

    private val borrowedAt = 1_700_000_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KoshtDatabase::class.java).build()
        repository = TransactionRepository(
            db.transactionDao(),
            db.categoryDao(),
            db.recurringDao(),
            db.transactionItemDao(),
            db.debtDao(),
            db.syncDao(),
            PhotoStore(context)
        )
        debtCategoryId = db.categoryDao().insert(
            CategoryEntity(
                key = "debt_expense", name = "", iconKey = "debt",
                colorArgb = 0xFFF4511E, type = TransactionType.EXPENSE, position = 0
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun owe(amountMinor: Long): Long = db.debtDao().insert(
        DebtEntity(
            personName = "Иван",
            direction = DebtDirection.I_OWE,
            amountMinor = amountMinor,
            currencyCode = "BYN",
            createdAt = borrowedAt
        )
    )

    private fun record(amountMinor: Long, debtId: Long, delta: Long) = TransactionEntity(
        amountMinor = amountMinor,
        type = TransactionType.EXPENSE,
        categoryId = debtCategoryId,
        timestamp = borrowedAt + 1,
        createdAt = borrowedAt + 1,
        debtId = debtId,
        debtDeltaMinor = delta
    )

    @Test
    fun deletingARepaymentPutsTheDebtBackAsItWas() = runBlocking {
        val debtId = owe(10_000)
        val id = db.transactionDao().insert(record(4_000, debtId, delta = 4_000))
        db.debtDao().update(db.debtDao().getById(debtId)!!.copy(amountMinor = 6_000))

        val deleted = repository.remove(db.transactionDao().getById(id)!!.transaction)

        assertEquals(10_000L, db.debtDao().getById(debtId)?.amountMinor)
        assertEquals(borrowedAt, db.debtDao().getById(debtId)?.createdAt)

        repository.restore(deleted)

        assertEquals(6_000L, db.debtDao().getById(debtId)?.amountMinor)
        assertEquals(borrowedAt, db.debtDao().getById(debtId)?.createdAt)
        assertNotNull(db.transactionDao().getById(id))
    }

    @Test
    fun closingADebtIsUndoneTogetherWithItsRecord() = runBlocking {
        val debtId = owe(8_000)
        val id = db.transactionDao().insert(record(8_000, debtId, delta = 0))
        db.debtDao().update(db.debtDao().getById(debtId)!!.copy(closedAt = borrowedAt + 2))

        val deleted = repository.remove(db.transactionDao().getById(id)!!.transaction)

        assertEquals(8_000L, db.debtDao().getById(debtId)?.amountMinor)
        assertNull(db.debtDao().getById(debtId)?.closedAt)

        repository.restore(deleted)

        assertEquals(borrowedAt + 2, db.debtDao().getById(debtId)?.closedAt)
    }

    @Test
    fun deletingTheRecord_thatOpenedTheDebt_takesTheDebtWithIt() = runBlocking {
        val debtId = owe(5_000)
        val id = db.transactionDao().insert(record(5_000, debtId, delta = -5_000))

        val deleted = repository.remove(db.transactionDao().getById(id)!!.transaction)

        assertNull(db.debtDao().getById(debtId))

        repository.restore(deleted)

        val back = db.debtDao().getById(debtId)
        assertEquals(5_000L, back?.amountMinor)
        assertEquals(borrowedAt, back?.createdAt)
        assertEquals("Иван", back?.personName)
    }

    @Test
    fun onlyTheDebtBornAsARecordCountsAsAlreadyWrittenDown() = runBlocking {
        val born = owe(5_000)
        val byHand = owe(3_000)
        db.transactionDao().insert(record(5_000, born, delta = -5_000))
        db.transactionDao().insert(record(1_000, byHand, delta = 1_000))

        assertEquals(
            listOf(born),
            db.transactionDao().observeDebtsBornAsRecord().first()
        )
    }

    @Test
    fun restoringDropsTheTombstoneSoTheRecordIsNotDeletedAgainOnSync() = runBlocking {
        val id = db.transactionDao().insert(
            TransactionEntity(
                amountMinor = 1_200,
                type = TransactionType.EXPENSE,
                categoryId = debtCategoryId,
                timestamp = borrowedAt,
                createdAt = borrowedAt,
                sync = SyncMeta(uid = "own-uid", updatedAt = borrowedAt)
            )
        )
        val stored = db.transactionDao().getById(id)!!.transaction
        val deleted = repository.remove(stored)
        // The trigger that writes tombstones lives on the real database; the
        // in-memory one is built without it, so the headstone goes in by hand.
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO sync_tombstones (entity, uid, deletedAt) VALUES ('transactions', ?, ?)",
            arrayOf<Any?>(stored.sync.uid, borrowedAt)
        )
        assertEquals(1, db.syncDao().tombstones().count { it.uid == stored.sync.uid })

        repository.restore(deleted)
        assertEquals(0, db.syncDao().tombstones().count { it.uid == stored.sync.uid })
    }
}
