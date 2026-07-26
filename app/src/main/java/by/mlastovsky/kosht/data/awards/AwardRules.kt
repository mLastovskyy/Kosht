package by.mlastovsky.kosht.data.awards

import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the awards are judged on, gathered in one place so the rules
 * below stay pure arithmetic.
 */
data class AwardStats(
    val txCount: Int = 0,
    val incomeCount: Int = 0,
    val photoCount: Int = 0,
    /** Records written between midnight and five in the morning. */
    val nightCount: Int = 0,
    val expenseCategoriesUsed: Int = 0,
    /** Consecutive days ending today with spending inside the daily budget. */
    val streakDays: Int = 0,
    val dailyBudgetMinor: Long = 0,
    /** Calendar months with at least one record. */
    val monthsTracked: Int = 0,
    /** Longest run of consecutive months that ended in the black. */
    val surplusMonthsInRow: Int = 0,
    /** Finished months where not a single day went over the budget. */
    val perfectMonths: Int = 0,
    /** This month, so far, is in the black. */
    val monthSurplus: Boolean = false,
    val savingsAny: Boolean = false,
    val savedBynMinor: Long = 0,
    val goalsCount: Int = 0,
    val goalsAchieved: Int = 0,
    val debtsClosed: Int = 0,
    val challenges: List<ChallengeProgress> = emptyList()
) {
    val challengesDone: Int get() = challenges.count { it.status == ChallengeStatus.DONE }
}

/**
 * One award, met or not, with the numbers behind it when there are any —
 * that is what the detail dialog turns into "6 / 10".
 */
data class AwardProgress(
    val key: String,
    val met: Boolean,
    val current: Long? = null,
    val target: Long? = null,
    /** Show the pair as money rather than a plain count. */
    val money: Boolean = false
)

/**
 * The award list, easiest first — which is also the order they are laid out
 * in on screen, six to a page.
 *
 * Kept free of Android and of any clock so the whole ladder can be unit
 * tested: an award that unlocks by accident is worse than no award at all,
 * because it can never be taken back.
 */
object AwardRules {

    /** 1000 BYN set aside, in minor units. */
    const val BIG_SAVER_TARGET_MINOR = 100_000L

    /** 5000 BYN — the same thing five times over. */
    const val FORTUNE_TARGET_MINOR = 500_000L

    fun evaluate(stats: AwardStats): List<AwardProgress> = with(stats) {
        listOf(
            // --- Getting started ---
            award("first_steps", txCount >= 1),
            award("income_first", incomeCount >= 1),
            award("ten", txCount >= 10, txCount.toLong(), 10),
            award("scanner", photoCount >= 1),
            award("saver", savingsAny),
            award("first_goal", goalsCount >= 1),

            // --- Getting the habit ---
            award("streak7", streakDays >= 7, streakDays.toLong(), 7),
            award("surplus", monthSurplus),
            award("goal_done", goalsAchieved >= 1),
            award("challenge_done", challengesDone >= 1),
            award("night_owl", nightCount >= 1),
            award("debt_closed", debtsClosed >= 1),

            // --- Getting serious ---
            award("photo10", photoCount >= 10, photoCount.toLong(), 10),
            award("hundred", txCount >= 100, txCount.toLong(), 100),
            award("streak30", streakDays >= 30, streakDays.toLong(), 30),
            award(
                "categories10",
                expenseCategoriesUsed >= 10,
                expenseCategoriesUsed.toLong(),
                10
            ),
            award(
                "big_saver",
                savedBynMinor >= BIG_SAVER_TARGET_MINOR,
                savedBynMinor,
                BIG_SAVER_TARGET_MINOR,
                money = true
            ),
            award("goal_three", goalsAchieved >= 3, goalsAchieved.toLong(), 3),

            // --- The hard half ---
            award("challenge_five", challengesDone >= 5, challengesDone.toLong(), 5),
            award("five_hundred", txCount >= 500, txCount.toLong(), 500),
            award("perfect_month", perfectMonths >= 1),
            award("surplus_three", surplusMonthsInRow >= 3, surplusMonthsInRow.toLong(), 3),
            award("photo100", photoCount >= 100, photoCount.toLong(), 100),
            award(
                "fortune",
                savedBynMinor >= FORTUNE_TARGET_MINOR,
                savedBynMinor,
                FORTUNE_TARGET_MINOR,
                money = true
            ),

            // --- The long game ---
            award("streak100", streakDays >= 100, streakDays.toLong(), 100),
            award("year_tracked", monthsTracked >= 12, monthsTracked.toLong(), 12),
            award("goal_ten", goalsAchieved >= 10, goalsAchieved.toLong(), 10),
            award("challenge_twenty", challengesDone >= 20, challengesDone.toLong(), 20),
            award("thousand", txCount >= 1000, txCount.toLong(), 1000),
            award("streak365", streakDays >= 365, streakDays.toLong(), 365)
        )
    }

    /** Every award key, in display order. */
    val keys: List<String> = evaluate(AwardStats()).map { it.key }

    private fun award(
        key: String,
        met: Boolean,
        current: Long? = null,
        target: Long? = null,
        money: Boolean = false
    ) = AwardProgress(key, met, current, target, money)
}

/**
 * The two counts that need more than a database query: whole months lived
 * inside the budget, and months in the black one after another.
 */
object AwardMath {

    /** Both sides of one month, as the awards need them. */
    data class Month(val month: YearMonth, val income: Long, val expense: Long)

    /**
     * Finished calendar months in which no single day went over the budget.
     * Only months from the first record onwards count, and only months that
     * have actually ended — a month still running has not been survived yet.
     */
    fun perfectMonths(
        spendByDay: Map<LocalDate, Long>,
        budgetMinor: Long,
        firstRecordDay: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (budgetMinor <= 0 || firstRecordDay == null) return 0
        val firstWholeMonth = YearMonth.from(firstRecordDay).let {
            // A month joined halfway through was not lived through in full.
            if (firstRecordDay.dayOfMonth == 1) it else it.plusMonths(1)
        }
        val lastWholeMonth = YearMonth.from(today).minusMonths(1)
        if (firstWholeMonth.isAfter(lastWholeMonth)) return 0

        var count = 0
        var month = firstWholeMonth
        while (!month.isAfter(lastWholeMonth)) {
            val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
            // A month with nothing recorded at all proves nothing.
            val recorded = days.any { (spendByDay[it] ?: 0L) > 0L }
            if (recorded && days.all { (spendByDay[it] ?: 0L) <= budgetMinor }) count++
            month = month.plusMonths(1)
        }
        return count
    }

    /**
     * The longest run of consecutive months that ended with more coming in
     * than going out. A month with no spending at all is not a surplus, and a
     * gap in the calendar breaks the run.
     */
    fun longestSurplusRun(months: List<Month>): Int {
        var best = 0
        var run = 0
        var previous: YearMonth? = null
        months.sortedBy { it.month }.forEach { entry ->
            val surplus = entry.income > entry.expense && entry.expense > 0
            run = when {
                !surplus -> 0
                previous?.plusMonths(1) == entry.month -> run + 1
                else -> 1
            }
            previous = entry.month
            if (run > best) best = run
        }
        return best
    }
}
