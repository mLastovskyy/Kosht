package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.model.TransactionType

data class SeedCategory(
    val key: String,
    val iconKey: String,
    val colorArgb: Long,
    val type: TransactionType
)

object CategorySeed {

    const val DEBT_INCOME = "debt_income"
    const val DEBT_EXPENSE = "debt_expense"
    const val SAVINGS_EXPENSE = "savings_expense"
    const val SAVINGS_INCOME = "savings_income"

    val all: List<SeedCategory> = listOf(

        SeedCategory("groceries", "groceries", 0xFF43A047, TransactionType.EXPENSE),
        SeedCategory("cafe", "cafe", 0xFFFB8C00, TransactionType.EXPENSE),
        SeedCategory("transport", "transport", 0xFF1E88E5, TransactionType.EXPENSE),
        SeedCategory("housing", "housing", 0xFF8D6E63, TransactionType.EXPENSE),
        SeedCategory("health", "health", 0xFFE53935, TransactionType.EXPENSE),
        SeedCategory("clothes", "clothes", 0xFF8E24AA, TransactionType.EXPENSE),
        SeedCategory("entertainment", "entertainment", 0xFFD81B60, TransactionType.EXPENSE),
        SeedCategory("subscriptions", "subscriptions", 0xFF3949AB, TransactionType.EXPENSE),
        SeedCategory("gifts", "gifts", 0xFFF06292, TransactionType.EXPENSE),
        SeedCategory("education", "education", 0xFF00ACC1, TransactionType.EXPENSE),
        SeedCategory("travel", "travel", 0xFF039BE5, TransactionType.EXPENSE),
        SeedCategory(DEBT_EXPENSE, "debt", 0xFFF4511E, TransactionType.EXPENSE),
        SeedCategory(SAVINGS_EXPENSE, "savings", 0xFF00ACC1, TransactionType.EXPENSE),
        SeedCategory("other_expense", "other", 0xFF757575, TransactionType.EXPENSE),

        SeedCategory("salary", "salary", 0xFF2E7D32, TransactionType.INCOME),
        SeedCategory("freelance", "freelance", 0xFF00897B, TransactionType.INCOME),
        SeedCategory("gift_income", "gifts", 0xFFEC407A, TransactionType.INCOME),
        SeedCategory("investments", "investments", 0xFF5E35B1, TransactionType.INCOME),
        SeedCategory(DEBT_INCOME, "debt", 0xFFFFB300, TransactionType.INCOME),
        SeedCategory(SAVINGS_INCOME, "savings", 0xFF26A69A, TransactionType.INCOME),
        SeedCategory("other_income", "other", 0xFF757575, TransactionType.INCOME)
    )

    val addedInV17: List<SeedCategory> =
        all.filter { it.key in setOf(DEBT_INCOME, DEBT_EXPENSE, SAVINGS_EXPENSE, SAVINGS_INCOME) }
}
