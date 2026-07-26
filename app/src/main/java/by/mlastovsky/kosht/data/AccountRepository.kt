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

    /**
     * Sets the shown balance of an account by tuning its adjustment:
     * adjustment = target − transaction sum.
     */
    suspend fun setAccountBalance(account: AccountEntity, targetMinor: Long) {
        val balances = accountDao.observeBalances().first()
        val primaryId = observeAccounts().first().firstOrNull()?.id
        var txSum = balances.firstOrNull { it.accountId == account.id }?.balance ?: 0L
        if (account.id == primaryId) {
            txSum += balances.firstOrNull { it.accountId == null }?.balance ?: 0L
        }
        accountDao.setAdjustment(account.id, targetMinor - txSum)
    }

    /**
     * Updates the appearance of an account. A rename drops the built-in
     * [AccountEntity.key] so the custom name wins over localization.
     */
    suspend fun updateAccount(account: AccountEntity) = accountDao.update(account)

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
