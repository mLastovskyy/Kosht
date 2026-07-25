package by.mlastovsky.kosht

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: KoshtDatabase

    private var foodId = 0L
    private var salaryId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KoshtDatabase::class.java
        ).build()

        foodId = db.categoryDao().insert(
            CategoryEntity(
                key = "groceries", name = "", iconKey = "groceries",
                colorArgb = 0xFF43A047, type = TransactionType.EXPENSE, position = 0
            )
        )
        salaryId = db.categoryDao().insert(
            CategoryEntity(
                key = "salary", name = "", iconKey = "salary",
                colorArgb = 0xFF2E7D32, type = TransactionType.INCOME, position = 1
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tx(
        amount: Long,
        type: TransactionType,
        categoryId: Long,
        timestamp: Long = 1_000L
    ) = TransactionEntity(
        amountMinor = amount,
        type = type,
        categoryId = categoryId,
        note = "",
        timestamp = timestamp,
        createdAt = timestamp
    )

    @Test
    fun balanceIsIncomeMinusExpenses() = runBlocking {
        db.transactionDao().insert(tx(10_000, TransactionType.INCOME, salaryId))
        db.transactionDao().insert(tx(3_000, TransactionType.EXPENSE, foodId))
        db.transactionDao().insert(tx(2_000, TransactionType.EXPENSE, foodId))

        assertEquals(5_000L, db.transactionDao().observeBalance().first())
    }

    @Test
    fun totalsAreFilteredByTypeAndRange() = runBlocking {
        db.transactionDao().insert(tx(1_000, TransactionType.EXPENSE, foodId, timestamp = 100))
        db.transactionDao().insert(tx(2_000, TransactionType.EXPENSE, foodId, timestamp = 200))
        db.transactionDao().insert(tx(9_000, TransactionType.INCOME, salaryId, timestamp = 150))
        // Outside of range:
        db.transactionDao().insert(tx(5_000, TransactionType.EXPENSE, foodId, timestamp = 500))

        val expenses = db.transactionDao()
            .observeTotal(TransactionType.EXPENSE, from = 100, to = 300).first()
        assertEquals(3_000L, expenses)
    }

    @Test
    fun categoryTotalsAreGroupedAndSorted() = runBlocking {
        val taxiId = db.categoryDao().insert(
            CategoryEntity(
                key = null, name = "Taxi", iconKey = "transport",
                colorArgb = 0xFF1E88E5, type = TransactionType.EXPENSE, position = 2
            )
        )
        db.transactionDao().insert(tx(1_000, TransactionType.EXPENSE, foodId))
        db.transactionDao().insert(tx(4_000, TransactionType.EXPENSE, taxiId))

        val totals = db.transactionDao()
            .observeCategoryTotals(TransactionType.EXPENSE, from = 0, to = 10_000).first()

        assertEquals(2, totals.size)
        assertEquals(taxiId, totals[0].categoryId)
        assertEquals(4_000L, totals[0].total)
    }

    @Test
    fun reassignCategoryMovesTransactions() = runBlocking {
        val customId = db.categoryDao().insert(
            CategoryEntity(
                key = null, name = "Coffee", iconKey = "coffee",
                colorArgb = 0xFFFB8C00, type = TransactionType.EXPENSE, position = 3
            )
        )
        val txId = db.transactionDao().insert(tx(700, TransactionType.EXPENSE, customId))

        db.transactionDao().reassignCategory(from = customId, to = foodId)
        db.categoryDao().deleteById(customId)

        val moved = db.transactionDao().getById(txId)
        assertEquals(foodId, moved?.transaction?.categoryId)
        assertEquals("groceries", moved?.category?.key)
    }

    @Test
    fun deletedTransactionCanBeRestoredWithSameId() = runBlocking {
        val id = db.transactionDao().insert(tx(1_500, TransactionType.EXPENSE, foodId))
        val stored = db.transactionDao().getById(id)!!.transaction

        db.transactionDao().delete(stored)
        assertEquals(null, db.transactionDao().getById(id))

        db.transactionDao().insert(stored)
        assertEquals(1_500L, db.transactionDao().getById(id)?.transaction?.amountMinor)
    }
}
