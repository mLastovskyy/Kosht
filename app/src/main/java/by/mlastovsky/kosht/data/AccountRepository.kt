package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.AccountBalance
import android.net.Uri
import by.mlastovsky.kosht.data.db.AccountDao
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AccountRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val recurringDao: RecurringDao,
    private val photoStore: PhotoStore
) {

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeBalances(): Flow<List<AccountBalance>> = accountDao.observeBalances()

    suspend fun setAccountBalance(account: AccountEntity, targetMinor: Long) {
        val balances = accountDao.observeBalances().first()
        val primaryId = observeAccounts().first().firstOrNull()?.id
        var txSum = balances.firstOrNull { it.accountId == account.id }?.balance ?: 0L
        if (account.id == primaryId) {
            txSum += balances.firstOrNull { it.accountId == null }?.balance ?: 0L
        }
        accountDao.setAdjustment(account.id, targetMinor - txSum)
    }

    suspend fun updateAccount(account: AccountEntity) = accountDao.update(account)

    suspend fun addAccount(
        name: String,
        iconKey: String,
        colorArgb: Long,
        iconUri: Uri? = null
    ): Long =
        accountDao.insert(
            AccountEntity(
                key = null,
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                position = accountDao.maxPosition() + 1,
                iconPath = iconUri?.let { photoStore.saveFromUri(it, ACCOUNT_ICONS) }
            )
        )

    suspend fun updateAppearance(
        account: AccountEntity,
        name: String,
        iconKey: String,
        colorArgb: Long,
        renamed: Boolean,
        iconUri: Uri?,
        clearIcon: Boolean
    ) {
        if (name.isBlank()) return
        val saved = iconUri?.let { photoStore.saveFromUri(it, ACCOUNT_ICONS) }
        val iconPath = when {
            saved != null -> saved
            clearIcon -> null
            else -> account.iconPath
        }
        if (iconPath != account.iconPath) photoStore.delete(account.iconPath)
        accountDao.update(
            account.copy(
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                key = if (renamed) null else account.key,
                iconPath = iconPath
            )
        )
    }

    suspend fun deleteAccount(account: AccountEntity) {
        if (accountDao.count() <= 1) return
        val primary = observeAccounts().first().firstOrNull { it.id != account.id } ?: return
        transactionDao.deleteCollapsedTransfers(from = account.id, to = primary.id)
        transactionDao.reassignAccount(from = account.id, to = primary.id)
        transactionDao.reassignTransferAccount(from = account.id, to = primary.id)
        recurringDao.reassignAccount(from = account.id, to = primary.id)
        accountDao.deleteById(account.id)
        photoStore.delete(account.iconPath)
    }

    private companion object {
        const val ACCOUNT_ICONS = "categories"
    }
}
