package by.mlastovsky.kosht

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.db.SavingEntity
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
class SavingEditTest {

    private lateinit var db: KoshtDatabase
    private lateinit var repository: WalletRepository

    private val setAsideAt = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KoshtDatabase::class.java).build()
        repository = WalletRepository(
            db.debtDao(),
            db.savingDao(),
            db.recurringDao(),
            db.transactionDao(),
            db.categoryDao(),
            db.goalDao(),
            db.challengeDao(),
            db.awardDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun stored(id: Long): SavingEntity =
        db.savingDao().observeRecent(20).first().first { it.id == id }

    private suspend fun goalTotal(goalId: Long): Long = db.goalDao()
        .observeProgress()
        .first()
        .firstOrNull { it.goalId == goalId }
        ?.total
        ?: 0L

    @Test
    fun editingASavingCarriesTheAmountDateAndGoalAcross() = runBlocking {
        val goalId = repository.addGoal("Отпуск", 100_000, "BYN")
        val id = repository.addSaving(20_000, "BYN", "аванс", goalId)

        repository.updateSaving(
            stored(id).copy(
                amountMinor = 30_000,
                note = "аванс и премия",
                timestamp = setAsideAt,
                goalId = null
            )
        )

        val after = stored(id)
        assertEquals(30_000L, after.amountMinor)
        assertEquals("аванс и премия", after.note)
        assertEquals(setAsideAt, after.timestamp)
        assertNull(after.goalId)
        assertEquals(0L, goalTotal(goalId))
    }

    @Test
    fun raisingASavingUpToTheTargetMarksTheGoalAchieved() = runBlocking {
        val goalId = repository.addGoal("Ноутбук", 50_000, "BYN")
        val id = repository.addSaving(10_000, "BYN", "", goalId)
        assertNull(db.goalDao().getById(goalId)?.achievedAt)

        repository.updateSaving(stored(id).copy(amountMinor = 50_000))

        assertEquals(50_000L, goalTotal(goalId))
        assertNotNull(db.goalDao().getById(goalId)?.achievedAt)
    }

    @Test
    fun aWithdrawalKeepsItsSignThroughAnEdit() = runBlocking {
        val id = repository.addSaving(-5_000, "BYN", "снял", null)

        repository.updateSaving(stored(id).copy(amountMinor = -7_500, currencyCode = "USD"))

        val after = stored(id)
        assertEquals(-7_500L, after.amountMinor)
        assertEquals("USD", after.currencyCode)
    }
}
