package by.mlastovsky.kosht.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.awards.AwardProgress
import by.mlastovsky.kosht.data.awards.AwardRules
import by.mlastovsky.kosht.data.awards.AwardTracker
import by.mlastovsky.kosht.data.awards.ChallengeProgress
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.components.CategoryEdit
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BadgeUi(
    val key: String,
    val unlocked: Boolean,

    val unlockedAt: Long? = null,

    val progressText: String? = null
)

data class AchievementsUiState(
    val loaded: Boolean = false,
    val streakDays: Int = 0,
    val dailyBudgetMinor: Long = 0,
    val challenges: List<ChallengeProgress> = emptyList(),
    val badges: List<BadgeUi> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY
)

class AchievementsViewModel(
    private val walletRepository: WalletRepository,
    private val tracker: AwardTracker,
    private val transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = combine(
        tracker.stats,
        tracker.earned,
        transactionRepository.observeCategories(TransactionType.EXPENSE),
        settingsRepository.settings
    ) { stats, earned, categories, settings ->
        if (stats == null) {
            AchievementsUiState(currencyCode = settings.currencyCode)
        } else {
            AchievementsUiState(
                loaded = true,
                streakDays = stats.streakDays,
                dailyBudgetMinor = stats.dailyBudgetMinor,
                challenges = stats.challenges,
                badges = AwardRules.evaluate(stats).map { award ->
                    val storedAt = earned[award.key]
                    val unlocked = award.met || storedAt != null
                    BadgeUi(
                        key = award.key,
                        unlocked = unlocked,
                        unlockedAt = storedAt,
                        progressText = progressText(award, unlocked)
                    )
                },
                expenseCategories = categories,
                currencyCode = settings.currencyCode
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

    private fun progressText(
        award: AwardProgress,
        unlocked: Boolean
    ): String? {
        if (unlocked) return null
        val current = award.current ?: return null
        val target = award.target ?: return null
        return if (award.money) {
            Money.format(current.coerceAtMost(target), "BYN") +
                " / " + Money.format(target, "BYN")
        } else {
            "${current.coerceAtMost(target)} / $target"
        }
    }

    fun addCategory(edit: CategoryEdit, type: TransactionType, onCreated: (Long) -> Unit) {
        if (edit.name.isBlank()) return
        viewModelScope.launch {
            onCreated(
                transactionRepository.addCategory(
                    edit.name,
                    edit.iconKey,
                    edit.colorArgb,
                    type,
                    edit.iconUri
                )
            )
        }
    }

    fun reorderCategories(ids: List<Long>) {
        viewModelScope.launch { transactionRepository.reorderCategories(ids) }
    }

    fun updateCategory(id: Long, edit: CategoryEdit) {
        viewModelScope.launch {
            transactionRepository.updateCategory(
                id,
                edit.name,
                edit.iconKey,
                edit.colorArgb,
                edit.iconUri,
                edit.iconCleared
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { transactionRepository.deleteCategory(category) }
    }

    fun addChallenge(
        type: ChallengeType,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        end: LocalDate
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            walletRepository.addChallenge(
                type = type,
                title = title,
                amountMinor = amountMinor,
                categoryId = categoryId,
                start = LocalDate.now(),
                end = end
            )
        }
    }

    fun updateChallenge(
        challenge: ChallengeProgress,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        end: LocalDate
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            walletRepository.updateChallenge(
                challenge.entity.copy(
                    title = title.trim(),
                    amountMinor = amountMinor,
                    categoryId = categoryId,
                    endEpochDay = end.toEpochDay()
                )
            )
        }
    }

    fun deleteChallenge(challenge: ChallengeProgress) {
        viewModelScope.launch { walletRepository.deleteChallenge(challenge.entity.id) }
    }
}
