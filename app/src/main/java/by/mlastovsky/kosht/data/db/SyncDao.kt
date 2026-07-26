package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class UidRef(val id: Long, val uid: String)

@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_cursor WHERE id = 0")
    suspend fun cursor(): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursor WHERE id = 0")
    fun observeCursor(): Flow<SyncCursorEntity?>

    @Query(
        "UPDATE sync_cursor SET pulledThrough = :pulled, pushedThrough = :pushed, " +
            "lastSyncAt = :lastSyncAt WHERE id = 0"
    )
    suspend fun saveCursor(pulled: Long, pushed: Long, lastSyncAt: Long)

    @Query("UPDATE sync_cursor SET pulledThrough = 0, pushedThrough = -1, lastSyncAt = 0 WHERE id = 0")
    suspend fun resetCursor()

    @Query("SELECT * FROM sync_tombstones")
    suspend fun tombstones(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE entity = :entity AND uid = :uid")
    suspend fun dropTombstone(entity: String, uid: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt <= :through")
    suspend fun dropTombstonesThrough(through: Long)

    @Query("SELECT id, uid FROM categories")
    suspend fun categoryRefs(): List<UidRef>

    @Query("SELECT id, uid FROM accounts")
    suspend fun accountRefs(): List<UidRef>

    @Query("SELECT id, uid FROM saving_goals")
    suspend fun goalRefs(): List<UidRef>

    @Query("SELECT id, uid FROM transactions")
    suspend fun transactionRefs(): List<UidRef>

    @Query("SELECT * FROM accounts WHERE updatedAt > :since")
    suspend fun accountsChanged(since: Long): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE uid IN (:uids)")
    suspend fun accountsByUid(uids: List<String>): List<AccountEntity>

    @Insert
    suspend fun insertAccount(row: AccountEntity)

    @Update
    suspend fun updateAccount(row: AccountEntity)

    @Query("DELETE FROM accounts WHERE uid = :uid")
    suspend fun deleteAccount(uid: String)

    @Query("SELECT * FROM categories WHERE updatedAt > :since")
    suspend fun categoriesChanged(since: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE uid IN (:uids)")
    suspend fun categoriesByUid(uids: List<String>): List<CategoryEntity>

    @Insert
    suspend fun insertCategory(row: CategoryEntity)

    @Update
    suspend fun updateCategory(row: CategoryEntity)

    @Query("DELETE FROM categories WHERE uid = :uid")
    suspend fun deleteCategory(uid: String)

    @Query("SELECT * FROM saving_goals WHERE updatedAt > :since")
    suspend fun goalsChanged(since: Long): List<SavingGoalEntity>

    @Query("SELECT * FROM saving_goals WHERE uid IN (:uids)")
    suspend fun goalsByUid(uids: List<String>): List<SavingGoalEntity>

    @Insert
    suspend fun insertGoal(row: SavingGoalEntity)

    @Update
    suspend fun updateGoal(row: SavingGoalEntity)

    @Query("DELETE FROM saving_goals WHERE uid = :uid")
    suspend fun deleteGoal(uid: String)

    @Query("SELECT * FROM transactions WHERE updatedAt > :since")
    suspend fun transactionsChanged(since: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE uid IN (:uids)")
    suspend fun transactionsByUid(uids: List<String>): List<TransactionEntity>

    @Insert
    suspend fun insertTransaction(row: TransactionEntity)

    @Update
    suspend fun updateTransaction(row: TransactionEntity)

    @Query("DELETE FROM transactions WHERE uid = :uid")
    suspend fun deleteTransaction(uid: String)

    @Query("SELECT * FROM transaction_items WHERE updatedAt > :since")
    suspend fun itemsChanged(since: Long): List<TransactionItemEntity>

    @Query("SELECT * FROM transaction_items WHERE uid IN (:uids)")
    suspend fun itemsByUid(uids: List<String>): List<TransactionItemEntity>

    @Insert
    suspend fun insertItem(row: TransactionItemEntity)

    @Update
    suspend fun updateItem(row: TransactionItemEntity)

    @Query("DELETE FROM transaction_items WHERE uid = :uid")
    suspend fun deleteItem(uid: String)

    @Query("SELECT * FROM recurring WHERE updatedAt > :since")
    suspend fun recurringChanged(since: Long): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE uid IN (:uids)")
    suspend fun recurringByUid(uids: List<String>): List<RecurringEntity>

    @Insert
    suspend fun insertRecurring(row: RecurringEntity)

    @Update
    suspend fun updateRecurring(row: RecurringEntity)

    @Query("DELETE FROM recurring WHERE uid = :uid")
    suspend fun deleteRecurring(uid: String)

    @Query("SELECT * FROM savings WHERE updatedAt > :since")
    suspend fun savingsChanged(since: Long): List<SavingEntity>

    @Query("SELECT * FROM savings WHERE uid IN (:uids)")
    suspend fun savingsByUid(uids: List<String>): List<SavingEntity>

    @Insert
    suspend fun insertSaving(row: SavingEntity)

    @Update
    suspend fun updateSaving(row: SavingEntity)

    @Query("DELETE FROM savings WHERE uid = :uid")
    suspend fun deleteSaving(uid: String)

    @Query("SELECT * FROM challenges WHERE updatedAt > :since")
    suspend fun challengesChanged(since: Long): List<ChallengeEntity>

    @Query("SELECT * FROM challenges WHERE uid IN (:uids)")
    suspend fun challengesByUid(uids: List<String>): List<ChallengeEntity>

    @Insert
    suspend fun insertChallenge(row: ChallengeEntity)

    @Update
    suspend fun updateChallenge(row: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE uid = :uid")
    suspend fun deleteChallenge(uid: String)

    @Query("SELECT * FROM debts WHERE updatedAt > :since")
    suspend fun debtsChanged(since: Long): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE uid IN (:uids)")
    suspend fun debtsByUid(uids: List<String>): List<DebtEntity>

    @Insert
    suspend fun insertDebt(row: DebtEntity)

    @Update
    suspend fun updateDebt(row: DebtEntity)

    @Query("DELETE FROM debts WHERE uid = :uid")
    suspend fun deleteDebt(uid: String)

    @Query("SELECT * FROM awards WHERE updatedAt > :since")
    suspend fun awardsChanged(since: Long): List<AwardEntity>

    @Query("SELECT * FROM awards WHERE uid IN (:uids)")
    suspend fun awardsByUid(uids: List<String>): List<AwardEntity>

    @Insert
    suspend fun insertAward(row: AwardEntity)

    @Update
    suspend fun updateAward(row: AwardEntity)

    @Query("DELETE FROM awards WHERE uid = :uid")
    suspend fun deleteAward(uid: String)
}
