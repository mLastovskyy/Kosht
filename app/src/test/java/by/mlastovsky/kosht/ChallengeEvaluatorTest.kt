package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.awards.ChallengeEvaluator
import by.mlastovsky.kosht.data.awards.ChallengeStatus
import by.mlastovsky.kosht.data.awards.DaySpend
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.util.Dates
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeEvaluatorTest {

    private val start = LocalDate.of(2026, 7, 1)
    private val end = LocalDate.of(2026, 7, 7)
    private val rates = mapOf(
        "BYN" to RateEntity("BYN", 1, 1.0, 0),
        "USD" to RateEntity("USD", 1, 3.2, 0)
    )

    private fun challenge(
        type: ChallengeType,
        amountMinor: Long,
        categoryId: Long? = null
    ) = ChallengeEntity(
        type = type,
        title = "test",
        amountMinor = amountMinor,
        categoryId = categoryId,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end.toEpochDay(),
        createdAt = 0
    )

    private fun spend(day: LocalDate, minor: Long, categoryId: Long = 1L) =
        DaySpend(day, categoryId, minor)

    private fun saving(day: LocalDate, minor: Long, currency: String = "BYN") =
        SavingEntity(
            amountMinor = minor,
            currencyCode = currency,
            timestamp = Dates.toEpochMillis(day)
        )

    @Test
    fun `a limit still has room while the period runs`() {
        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SPEND_LIMIT, 100_00),
            spend = listOf(spend(start, 30_00)),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 3)
        )
        assertEquals(ChallengeStatus.ACTIVE, result.status)
        assertEquals(30_00L, result.progressLabelMinor)
        assertEquals(0.3f, result.progress, 1e-4f)
        assertEquals(3, result.daysPassed)
        assertEquals(7, result.daysTotal)
    }

    @Test
    fun `going over the limit fails it immediately`() {
        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SPEND_LIMIT, 100_00),
            spend = listOf(spend(start, 60_00), spend(start.plusDays(1), 50_00)),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 3)
        )
        assertEquals(ChallengeStatus.FAILED, result.status)
    }

    @Test
    fun `staying inside the limit to the end passes it`() {
        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SPEND_LIMIT, 100_00),
            spend = listOf(spend(start, 60_00)),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 8)
        )
        assertEquals(ChallengeStatus.DONE, result.status)
    }

    @Test
    fun `a limit on one category ignores every other`() {
        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SPEND_LIMIT, 50_00, categoryId = 7L),
            spend = listOf(spend(start, 90_00, categoryId = 3L), spend(start, 10_00, categoryId = 7L)),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 2)
        )
        assertEquals(ChallengeStatus.ACTIVE, result.status)
        assertEquals(10_00L, result.progressLabelMinor)
    }

    @Test
    fun `spending outside the period does not count`() {
        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SPEND_LIMIT, 10_00),
            spend = listOf(spend(start.minusDays(1), 500_00), spend(end.plusDays(1), 500_00)),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 4)
        )
        assertEquals(ChallengeStatus.ACTIVE, result.status)
        assertEquals(0L, result.progressLabelMinor)
    }

    @Test
    fun `one expense breaks a no-spend run`() {
        val broken = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.NO_SPEND, 0),
            spend = listOf(spend(start.plusDays(2), 1_00)),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 4)
        )
        assertEquals(ChallengeStatus.FAILED, broken.status)

        val kept = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.NO_SPEND, 0),
            spend = emptyList(),
            savings = emptyList(),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 8)
        )
        assertEquals(ChallengeStatus.DONE, kept.status)
    }

    @Test
    fun `a savings target compares currencies through byn`() {

        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SAVE_TARGET, 100_00),
            spend = emptyList(),
            savings = listOf(
                saving(start, 200_00, "BYN"),
                saving(start.plusDays(1), 50_00, "USD")
            ),
            rates = rates,
            currencyCode = "USD",
            today = LocalDate.of(2026, 7, 3)
        )
        assertEquals(ChallengeStatus.DONE, result.status)
        assertEquals(360_00L, result.progressLabelMinor)
    }

    @Test
    fun `a savings target missed by the deadline fails`() {
        val result = ChallengeEvaluator.evaluate(
            challenge = challenge(ChallengeType.SAVE_TARGET, 100_00),
            spend = emptyList(),
            savings = listOf(saving(start, 10_00)),
            rates = rates,
            currencyCode = "BYN",
            today = LocalDate.of(2026, 7, 8)
        )
        assertEquals(ChallengeStatus.FAILED, result.status)
    }
}
