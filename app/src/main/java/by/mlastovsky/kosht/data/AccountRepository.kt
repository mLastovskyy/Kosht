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
    private val transactionDao: TransactionDao,
    private val recurringDao: by.mlastovsky.kosht.data.db.RecurringDao
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
     *
     * Transfers are the one thing that cannot simply be moved: one between
     * this account and the account inheriting its records would end up going
     * from itself to itself, so those go with the account.
     */
    suspend fun deleteAccount(account: AccountEntity) {
        if (accountDao.count() <= 1) return
        val primary = observeAccounts().first().firstOrNull { it.id != account.id } ?: return
        transactionDao.deleteCollapsedTransfers(from = account.id, to = primary.id)
        transactionDao.reassignAccount(from = account.id, to = primary.id)
        transactionDao.reassignTransferAccount(from = account.id, to = primary.id)
        recurringDao.reassignAccount(from = account.id, to = primary.id)
        accountDao.deleteById(account.id)
    }
}
