package by.mlastovsky.kosht.data.awards

import java.time.LocalDate
import java.time.YearMonth

data class AwardStats(
    val txCount: Int = 0,
    val incomeCount: Int = 0,
    val photoCount: Int = 0,

    val nightCount: Int = 0,
    val expenseCategoriesUsed: Int = 0,

    val streakDays: Int = 0,
    val dailyBudgetMinor: Long = 0,

    val monthsTracked: Int = 0,

    val surplusMonthsInRow: Int = 0,

    val perfectMonths: Int = 0,

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

data class AwardProgress(
    val key: String,
    val met: Boolean,
    val current: Long? = null,
    val target: Long? = null,

    val money: Boolean = false
)

object AwardRules {

    const val BIG_SAVER_TARGET_MINOR = 100_000L

    const val FORTUNE_TARGET_MINOR = 500_000L

    const val TREASURY_TARGET_MINOR = 2_000_000L

    const val MILLIONAIRE_TARGET_MINOR = 10_000_000L

    fun evaluate(stats: AwardStats): List<AwardProgress> = with(stats) {
        listOf(

            award("first_steps", txCount >= 1),
            award("income_first", incomeCount >= 1),
            award("ten", txCount >= 10, txCount.toLong(), 10),
            award("scanner", photoCount >= 1),
            award("saver", savingsAny),
            award("first_goal", goalsCount >= 1),

            award("streak7", streakDays >= 7, streakDays.toLong(), 7),
            award("surplus", monthSurplus),
            award("goal_done", goalsAchieved >= 1),
            award("challenge_done", challengesDone >= 1),
            award("night_owl", nightCount >= 1),
            award("debt_closed", debtsClosed >= 1),

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

            award("streak100", streakDays >= 100, streakDays.toLong(), 100),
            award("year_tracked", monthsTracked >= 12, monthsTracked.toLong(), 12),
            award("goal_ten", goalsAchieved >= 10, goalsAchieved.toLong(), 10),
            award("challenge_twenty", challengesDone >= 20, challengesDone.toLong(), 20),
            award("thousand", txCount >= 1000, txCount.toLong(), 1000),
            award("streak365", streakDays >= 365, streakDays.toLong(), 365),

            award("night100", nightCount >= 100, nightCount.toLong(), 100),
            award(
                "categories20",
                expenseCategoriesUsed >= 20,
                expenseCategoriesUsed.toLong(),
                20
            ),
            award("photo500", photoCount >= 500, photoCount.toLong(), 500),
            award("debt_free", debtsClosed >= 10, debtsClosed.toLong(), 10),
            award("goal_25", goalsAchieved >= 25, goalsAchieved.toLong(), 25),
            award("challenge_fifty", challengesDone >= 50, challengesDone.toLong(), 50),

            award("surplus_year", surplusMonthsInRow >= 12, surplusMonthsInRow.toLong(), 12),
            award("perfect_year", perfectMonths >= 12, perfectMonths.toLong(), 12),
            award(
                "treasury",
                savedBynMinor >= TREASURY_TARGET_MINOR,
                savedBynMinor,
                TREASURY_TARGET_MINOR,
                money = true
            ),
            award("five_thousand", txCount >= 5000, txCount.toLong(), 5000),
            award("three_years", monthsTracked >= 36, monthsTracked.toLong(), 36),
            award(
                "millionaire",
                savedBynMinor >= MILLIONAIRE_TARGET_MINOR,
                savedBynMinor,
                MILLIONAIRE_TARGET_MINOR,
                money = true
            )
        )
    }

    val keys: List<String> = evaluate(AwardStats()).map { it.key }

    private fun award(
        key: String,
        met: Boolean,
        current: Long? = null,
        target: Long? = null,
        money: Boolean = false
    ) = AwardProgress(key, met, current, target, money)
}

object AwardMath {

    data class Month(val month: YearMonth, val income: Long, val expense: Long)

    fun perfectMonths(
        spendByDay: Map<LocalDate, Long>,
        budgetMinor: Long,
        firstRecordDay: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (budgetMinor <= 0 || firstRecordDay == null) return 0
        val firstWholeMonth = YearMonth.from(firstRecordDay).let {

            if (firstRecordDay.dayOfMonth == 1) it else it.plusMonths(1)
        }
        val lastWholeMonth = YearMonth.from(today).minusMonths(1)
        if (firstWholeMonth.isAfter(lastWholeMonth)) return 0

        var count = 0
        var month = firstWholeMonth
        while (!month.isAfter(lastWholeMonth)) {
            val days = (1..month.lengthOfMonth()).map { month.atDay(it) }

            val recorded = days.any { (spendByDay[it] ?: 0L) > 0L }
            if (recorded && days.all { (spendByDay[it] ?: 0L) <= budgetMinor }) count++
            month = month.plusMonths(1)
        }
        return count
    }

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
