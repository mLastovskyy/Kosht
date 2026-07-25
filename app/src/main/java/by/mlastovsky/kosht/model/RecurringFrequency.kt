package by.mlastovsky.kosht.model

import java.time.LocalDate

enum class RecurringFrequency {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    fun next(from: LocalDate): LocalDate = when (this) {
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonths(1)
        QUARTERLY -> from.plusMonths(3)
        YEARLY -> from.plusYears(1)
    }
}
