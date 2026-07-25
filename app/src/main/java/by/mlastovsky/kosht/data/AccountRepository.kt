package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.AccountBalance
import by.mlastovsky.kosht.data.db.AccountDao
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Money sources (cards, cash, ...). */
class AccountRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeBalances(): Flow<List<AccountBalance>> = accountDao.observeBalances()

    suspend fun addAccount(name: String, iconKey: String, colorArgb: Long): Long =
        accountDao.insert(
            AccountEntity(
                key = null,
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                position = accountDao.maxPosition() + 1
            )
        )

    /**
     * Deletes an account, moving its transactions to the primary one.
     * The last remaining account can not be deleted.
     */
    suspend fun deleteAccount(account: AccountEntity) {
        if (accountDao.count() <= 1) return
        val primary = observeAccounts().first().firstOrNull { it.id != account.id } ?: return
        transactionDao.reassignAccount(from = account.id, to = primary.id)
        accountDao.deleteById(account.id)
    }
}
