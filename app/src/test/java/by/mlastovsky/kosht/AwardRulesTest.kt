package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.awards.AwardMath
import by.mlastovsky.kosht.data.awards.AwardRules
import by.mlastovsky.kosht.data.awards.AwardStats
import by.mlastovsky.kosht.data.awards.ChallengeProgress
import by.mlastovsky.kosht.data.awards.ChallengeStatus
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.model.ChallengeType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwardRulesTest {

    private fun met(stats: AwardStats): Set<String> =
        AwardRules.evaluate(stats).filter { it.met }.map { it.key }.toSet()

    private fun done(count: Int): List<ChallengeProgress> = List(count) {
        ChallengeProgress(
            entity = ChallengeEntity(
                type = ChallengeType.NO_SPEND,
                title = "x",
                amountMinor = 0,
                startEpochDay = 0,
                endEpochDay = 1,
                createdAt = 0
            ),
            progress = 1f,
            progressLabelMinor = 0,
            daysPassed = 2,
            daysTotal = 2,
            status = ChallengeStatus.DONE
        )
    }

    @Test
    fun `an empty history earns nothing`() {
        assertTrue(met(AwardStats()).isEmpty())
    }

    @Test
    fun `every key is unique and the ladder is a whole number of pages`() {
        assertEquals(AwardRules.keys.size, AwardRules.keys.toSet().size)

        assertEquals(0, AwardRules.keys.size % 6)
    }

    @Test
    fun `the first record earns exactly the first award`() {
        assertEquals(setOf("first_steps"), met(AwardStats(txCount = 1)))
    }

    @Test
    fun `counting awards stack as the count grows`() {
        val many = met(AwardStats(txCount = 1000))
        assertTrue(many.containsAll(listOf("first_steps", "ten", "hundred", "five_hundred", "thousand")))
    }

    @Test
    fun `a locked count carries its progress and an earned one does not`() {
        val awards = AwardRules.evaluate(AwardStats(txCount = 6)).associateBy { it.key }
        assertEquals(6L, awards.getValue("ten").current)
        assertEquals(10L, awards.getValue("ten").target)
        assertTrue(awards.getValue("first_steps").met)
    }

    @Test
    fun `saving targets are judged in byn minor units`() {
        assertFalse("big_saver" in met(AwardStats(savedBynMinor = 99_999)))
        assertTrue("big_saver" in met(AwardStats(savedBynMinor = 100_000)))
        assertFalse("fortune" in met(AwardStats(savedBynMinor = 100_000)))
        assertTrue("fortune" in met(AwardStats(savedBynMinor = 500_000)))
    }

    @Test
    fun `streak awards unlock at their own lengths`() {
        assertEquals(
            setOf("streak7", "streak30"),
            met(AwardStats(streakDays = 44)).filter { it.startsWith("streak") }.toSet()
        )
        assertTrue("streak365" in met(AwardStats(streakDays = 365)))
    }

    @Test
    fun `finished challenges are counted, unfinished ones are not`() {
        val stats = AwardStats(challenges = done(5))
        assertEquals(5, stats.challengesDone)
        assertTrue(met(stats).containsAll(listOf("challenge_done", "challenge_five")))
        assertFalse("challenge_twenty" in met(stats))
    }

    @Test
    fun `a month in the black is not three months in the black`() {
        assertTrue("surplus" in met(AwardStats(monthSurplus = true)))
        assertFalse("surplus_three" in met(AwardStats(monthSurplus = true)))
        assertTrue("surplus_three" in met(AwardStats(surplusMonthsInRow = 3)))
    }

    @Test
    fun `an award with no number to show has no progress line`() {
        val night = AwardRules.evaluate(AwardStats()).first { it.key == "night_owl" }
        assertNull(night.current)
    }
}

class AwardMathTest {

    private fun month(year: Int, month: Int, income: Long, expense: Long) =
        AwardMath.Month(YearMonth.of(year, month), income, expense)

    @Test
    fun `three months in a row count, a broken run does not`() {
        val months = listOf(
            month(2026, 1, 300_00, 200_00),
            month(2026, 2, 300_00, 200_00),
            month(2026, 3, 300_00, 200_00)
        )
        assertEquals(3, AwardMath.longestSurplusRun(months))

        val broken = listOf(
            month(2026, 1, 300_00, 200_00),
            month(2026, 2, 100_00, 200_00),
            month(2026, 3, 300_00, 200_00)
        )
        assertEquals(1, AwardMath.longestSurplusRun(broken))
    }

    @Test
    fun `a gap in the calendar breaks the run`() {
        val skipped = listOf(
            month(2026, 1, 300_00, 200_00),
            month(2026, 3, 300_00, 200_00)
        )
        assertEquals(1, AwardMath.longestSurplusRun(skipped))
    }

    @Test
    fun `a month with income and no spending is not a surplus`() {
        assertEquals(0, AwardMath.longestSurplusRun(listOf(month(2026, 1, 300_00, 0))))
    }

    @Test
    fun `a whole month inside the budget counts once it is over`() {
        val january = (1..31).associate { LocalDate.of(2026, 1, it) to 40_00L }
        assertEquals(
            1,
            AwardMath.perfectMonths(
                spendByDay = january,
                budgetMinor = 50_00,
                firstRecordDay = LocalDate.of(2026, 1, 1),
                today = LocalDate.of(2026, 2, 10)
            )
        )
    }

    @Test
    fun `one day over the budget spoils the month`() {
        val january = (1..31).associate {
            LocalDate.of(2026, 1, it) to if (it == 17) 90_00L else 40_00L
        }
        assertEquals(
            0,
            AwardMath.perfectMonths(
                spendByDay = january,
                budgetMinor = 50_00,
                firstRecordDay = LocalDate.of(2026, 1, 1),
                today = LocalDate.of(2026, 2, 10)
            )
        )
    }

    @Test
    fun `the month being lived through does not count yet`() {
        val february = (1..10).associate { LocalDate.of(2026, 2, it) to 10_00L }
        assertEquals(
            0,
            AwardMath.perfectMonths(
                spendByDay = february,
                budgetMinor = 50_00,
                firstRecordDay = LocalDate.of(2026, 2, 1),
                today = LocalDate.of(2026, 2, 10)
            )
        )
    }

    @Test
    fun `a month joined halfway through was not lived through in full`() {
        val january = (20..31).associate { LocalDate.of(2026, 1, it) to 10_00L }
        assertEquals(
            0,
            AwardMath.perfectMonths(
                spendByDay = january,
                budgetMinor = 50_00,
                firstRecordDay = LocalDate.of(2026, 1, 20),
                today = LocalDate.of(2026, 2, 10)
            )
        )
    }

    @Test
    fun `a month with nothing recorded proves nothing`() {
        assertEquals(
            0,
            AwardMath.perfectMonths(
                spendByDay = mapOf(LocalDate.of(2026, 3, 4) to 10_00L),
                budgetMinor = 50_00,
                firstRecordDay = LocalDate.of(2026, 1, 1),
                today = LocalDate.of(2026, 3, 10)
            )
        )
    }
}
