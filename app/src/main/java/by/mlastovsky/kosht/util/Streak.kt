package by.mlastovsky.kosht.util

import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

object Streak {

    const val DEFAULT_BUDGET_MINOR = 50_00L

    fun spendByDay(transactions: List<TransactionWithCategory>): Map<LocalDate, Long> =
        transactions

            .filter { it.transaction.type == TransactionType.EXPENSE && !it.transaction.isTransfer }
            .groupBy { Dates.toLocalDate(it.transaction.timestamp) }
            .mapValues { (_, items) -> items.sumOf { it.transaction.amountMinor } }

    fun autoDailyBudget(
        spendByDay: Map<LocalDate, Long>,
        today: LocalDate = LocalDate.now()
    ): Long {
        val prevMonth = YearMonth.from(today).minusMonths(1)
        val prevTotal = spendByDay
            .filterKeys { YearMonth.from(it) == prevMonth }
            .values.sum()
        if (prevTotal > 0) {
            return (prevTotal / prevMonth.lengthOfMonth()).coerceAtLeast(1)
        }
        val currentTotal = spendByDay
            .filterKeys { YearMonth.from(it) == YearMonth.from(today) }
            .values.sum()
        if (currentTotal > 0) {
            return (currentTotal / today.dayOfMonth).coerceAtLeast(1)
        }
        return DEFAULT_BUDGET_MINOR
    }

    fun budgetStreak(
        spendByDay: Map<LocalDate, Long>,
        budgetMinor: Long,
        firstRecordDay: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (firstRecordDay == null || budgetMinor <= 0) return 0
        if ((spendByDay[today] ?: 0L) > budgetMinor) return 0
        var streak = if (!today.isBefore(firstRecordDay)) 1 else 0
        var cursor = today.minusDays(1)
        while (!cursor.isBefore(firstRecordDay) && (spendByDay[cursor] ?: 0L) <= budgetMinor) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
