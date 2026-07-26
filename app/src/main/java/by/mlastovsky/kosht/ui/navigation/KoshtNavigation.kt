package by.mlastovsky.kosht.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import by.mlastovsky.kosht.R

object Routes {
    /**
     * The five main screens together: one destination holding a pager, so they
     * can be swiped through as well as tapped. Everything that opens *over* a
     * tab — the editor, the guide, achievements — stays a destination of its own.
     */
    const val TABS = "tabs"

    const val HOME = "home"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val WALLET = "wallet"
    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"
    const val GUIDE = "guide"
    const val EDITOR = "editor?transactionId={transactionId}"

    const val EDITOR_ARG_ID = "transactionId"
    const val NO_ID = -1L

    fun editor(transactionId: Long? = null): String =
        "editor?transactionId=${transactionId ?: NO_ID}"
}

data class TabDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val MainTabs = listOf(
    TabDestination(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    TabDestination(
        Routes.HISTORY,
        R.string.nav_history,
        Icons.AutoMirrored.Rounded.ReceiptLong,
        Icons.AutoMirrored.Outlined.ReceiptLong
    ),
    TabDestination(
        Routes.STATS,
        R.string.nav_stats,
        Icons.Rounded.PieChart,
        Icons.Outlined.PieChart
    ),
    TabDestination(
        Routes.WALLET,
        R.string.nav_wallet,
        Icons.Rounded.AccountBalanceWallet,
        Icons.Outlined.AccountBalanceWallet
    ),
    TabDestination(
        Routes.SETTINGS,
        R.string.nav_settings,
        Icons.Rounded.Settings,
        Icons.Outlined.Settings
    )
)
