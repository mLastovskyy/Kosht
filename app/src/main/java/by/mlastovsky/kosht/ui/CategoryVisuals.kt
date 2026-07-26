package by.mlastovsky.kosht.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.CategorySeed
import by.mlastovsky.kosht.data.db.CategoryEntity

object CategoryVisuals {

    private val icons: Map<String, ImageVector> = mapOf(
        "groceries" to Icons.Rounded.ShoppingCart,
        "cafe" to Icons.Rounded.Restaurant,
        "transport" to Icons.Rounded.DirectionsBus,
        "housing" to Icons.Rounded.Home,
        "health" to Icons.Rounded.Favorite,
        "clothes" to Icons.Rounded.Checkroom,
        "entertainment" to Icons.Rounded.Movie,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "gifts" to Icons.Rounded.CardGiftcard,
        "education" to Icons.Rounded.School,
        "travel" to Icons.Rounded.Flight,
        "debt" to Icons.Rounded.Handshake,
        "other" to Icons.Rounded.Category,
        "salary" to Icons.Rounded.Payments,
        "freelance" to Icons.Rounded.Laptop,
        "investments" to Icons.AutoMirrored.Rounded.TrendingUp,

        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "card" to Icons.Rounded.CreditCard,
        "bank" to Icons.Rounded.AccountBalance,
        "savings" to Icons.Rounded.Savings,
        "car" to Icons.Rounded.DirectionsCar,
        "coffee" to Icons.Rounded.LocalCafe,
        "pets" to Icons.Rounded.Pets,
        "sports" to Icons.Rounded.FitnessCenter,
        "beauty" to Icons.Rounded.Spa,
        "kids" to Icons.Rounded.ChildCare,
        "phone" to Icons.Rounded.Smartphone,
        "music" to Icons.Rounded.MusicNote,
        "games" to Icons.Rounded.SportsEsports,
        "books" to Icons.AutoMirrored.Rounded.MenuBook,
        "tools" to Icons.Rounded.Build
    )

    val pickableIconKeys: List<String> = icons.keys.toList()

    val pickableColors: List<Long> = listOf(
        0xFF43A047, 0xFF2E7D32, 0xFF00897B, 0xFF00ACC1, 0xFF039BE5, 0xFF1E88E5,
        0xFF3949AB, 0xFF5E35B1, 0xFF8E24AA, 0xFFD81B60, 0xFFE53935, 0xFFF4511E,
        0xFFFB8C00, 0xFFFFB300, 0xFF8D6E63, 0xFF757575
    )

    fun icon(iconKey: String): ImageVector = icons[iconKey] ?: Icons.Rounded.Category

    @StringRes
    fun nameRes(key: String): Int? = when (key) {
        "groceries" -> R.string.category_groceries
        "cafe" -> R.string.category_cafe
        "transport" -> R.string.category_transport
        "housing" -> R.string.category_housing
        "health" -> R.string.category_health
        "clothes" -> R.string.category_clothes
        "entertainment" -> R.string.category_entertainment
        "subscriptions" -> R.string.category_subscriptions
        "gifts" -> R.string.category_gifts
        "education" -> R.string.category_education
        "travel" -> R.string.category_travel
        CategorySeed.DEBT_EXPENSE -> R.string.category_debt_expense
        CategorySeed.SAVINGS_EXPENSE -> R.string.category_savings_expense
        "other_expense" -> R.string.category_other_expense
        "salary" -> R.string.category_salary
        "freelance" -> R.string.category_freelance
        "gift_income" -> R.string.category_gift_income
        "investments" -> R.string.category_investments
        CategorySeed.DEBT_INCOME -> R.string.category_debt_income
        CategorySeed.SAVINGS_INCOME -> R.string.category_savings_income
        "other_income" -> R.string.category_other_income
        else -> null
    }

    @Composable
    fun displayName(category: CategoryEntity): String {
        if (category.name.isNotBlank()) return category.name
        val res = category.key?.let { nameRes(it) }
        return if (res != null) stringResource(res) else category.name
    }
}
