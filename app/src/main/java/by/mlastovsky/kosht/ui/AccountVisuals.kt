package by.mlastovsky.kosht.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.AccountEntity

object AccountVisuals {

    val pickableIconKeys = listOf("card", "wallet", "bank", "savings", "phone")

    @Composable
    fun displayName(account: AccountEntity): String = when (account.key) {
        "card" -> stringResource(R.string.account_card)
        "cash" -> stringResource(R.string.account_cash)
        else -> account.name
    }
}
