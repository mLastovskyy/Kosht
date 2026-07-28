package by.mlastovsky.kosht.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ReportField
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX
import by.mlastovsky.kosht.util.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val currencyCode: String,
    val themeMode: ThemeMode,
    val dynamicColors: Boolean,
    val notifyDailyReminder: Boolean,
    val notifyRecurringDue: Boolean,
    val notifyWeeklySummary: Boolean,

    val notifyAwards: Boolean,

    val dailyBudgetMinor: Long,
    val showGreeting: Boolean,
    val showStreak: Boolean,
    val showRates: Boolean,

    val convertOnCurrencyChange: Boolean,

    val multiAccount: Boolean,

    val transferFee: Boolean,

    val reportFields: Set<String>,

    val reportPeriod: String,

    val autoCalculator: Boolean,

    val syncPhotos: Boolean
)

data class SyncedSettings(

    val updatedAt: Long,
    val settings: AppSettings,
    val profileNickname: String,

    val profileEmoji: String?
)

data class UserProfile(
    val nickname: String,
    val photoPath: String?
) {

    fun displayName(fallback: String): String = nickname.ifBlank { fallback }
}

class SettingsRepository(private val context: Context) {

    val notificationsAsked: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.notificationsAsked] ?: false }

    suspend fun markNotificationsAsked() {
        context.dataStore.edit { it[Keys.notificationsAsked] = true }
    }

    val tourSeen: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.tourSeen] ?: false }

    suspend fun setTourSeen(value: Boolean) {
        context.dataStore.edit { it[Keys.tourSeen] = value }
    }

    val policyVersionSeen: Flow<String?> = context.dataStore.data
        .map { it[Keys.policyVersionSeen]?.takeIf { version -> version.isNotBlank() } }

    suspend fun setPolicyVersionSeen(version: String) {
        context.dataStore.edit { it[Keys.policyVersionSeen] = version }
    }

    private object Keys {
        val currencyCode = stringPreferencesKey("currency_code")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColors = booleanPreferencesKey("dynamic_colors")
        val notifyDailyReminder = booleanPreferencesKey("notify_daily_reminder")
        val notifyRecurringDue = booleanPreferencesKey("notify_recurring_due")
        val notifyWeeklySummary = booleanPreferencesKey("notify_weekly_summary")
        val notifyAwards = booleanPreferencesKey("notify_awards")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
        val tourSeen = booleanPreferencesKey("tour_seen")
        val retiredProfileName = stringPreferencesKey("profile_name")
        val profileNickname = stringPreferencesKey("profile_nickname")
        val profilePhotoPath = stringPreferencesKey("profile_photo_path")
        val avatarSeeded = booleanPreferencesKey("avatar_seeded")
        val dailyBudgetMinor = longPreferencesKey("daily_budget_minor")
        val showGreeting = booleanPreferencesKey("show_greeting")
        val showStreak = booleanPreferencesKey("show_streak")
        val showRates = booleanPreferencesKey("show_rates")
        val convertOnCurrencyChange = booleanPreferencesKey("convert_on_currency_change")
        val multiAccount = booleanPreferencesKey("multi_account")
        val transferFee = booleanPreferencesKey("transfer_fee")
        val policyVersionSeen = stringPreferencesKey("policy_version_seen")
        val reportFields = stringSetPreferencesKey("report_fields")
        val reportPeriod = stringPreferencesKey("report_period")
        val autoCalculator = booleanPreferencesKey("auto_calculator")
        val syncPhotos = booleanPreferencesKey("sync_photos")

        val updatedAt = longPreferencesKey("settings_updated_at")
    }

    private suspend fun bumped(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs ->
            block(prefs)
            prefs[Keys.updatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun setReportFields(fields: Set<String>) {
        bumped { it[Keys.reportFields] = fields }
    }

    suspend fun setReportPeriod(period: String) {
        bumped { it[Keys.reportPeriod] = period }
    }

    suspend fun setAutoCalculator(value: Boolean) {
        bumped { it[Keys.autoCalculator] = value }
    }

    suspend fun setSyncPhotos(value: Boolean) {
        bumped { it[Keys.syncPhotos] = value }
    }

    suspend fun setMultiAccount(value: Boolean) {
        bumped { it[Keys.multiAccount] = value }
    }

    suspend fun setTransferFee(value: Boolean) {
        bumped { it[Keys.transferFee] = value }
    }

    suspend fun setConvertOnCurrencyChange(value: Boolean) {
        bumped { it[Keys.convertOnCurrencyChange] = value }
    }

    suspend fun setShowGreeting(value: Boolean) {
        bumped { it[Keys.showGreeting] = value }
    }

    suspend fun setShowStreak(value: Boolean) {
        bumped { it[Keys.showStreak] = value }
    }

    suspend fun setShowRates(value: Boolean) {
        bumped { it[Keys.showRates] = value }
    }

    suspend fun setDailyBudgetMinor(value: Long) {
        bumped { it[Keys.dailyBudgetMinor] = value.coerceAtLeast(0) }
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            nickname = prefs[Keys.profileNickname]?.takeIf { it.isNotBlank() }
                ?: prefs[Keys.retiredProfileName].orEmpty(),
            photoPath = prefs[Keys.profilePhotoPath]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setProfile(nickname: String) {
        bumped {
            it[Keys.profileNickname] = nickname.trim().take(24)
            it.remove(Keys.retiredProfileName)
        }
    }

    suspend fun setProfilePhoto(path: String?) {
        bumped { it[Keys.profilePhotoPath] = path.orEmpty() }
    }

    suspend fun seedAvatarOnce(pick: () -> String) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.avatarSeeded] == true) return@edit
            prefs[Keys.avatarSeeded] = true
            if (prefs[Keys.profilePhotoPath].isNullOrBlank()) {
                prefs[Keys.profilePhotoPath] = pick()
                prefs[Keys.updatedAt] = System.currentTimeMillis()
            }
        }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            currencyCode = prefs[Keys.currencyCode] ?: DEFAULT_CURRENCY,
            themeMode = prefs[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColors = prefs[Keys.dynamicColors] ?: false,
            notifyDailyReminder = prefs[Keys.notifyDailyReminder] ?: false,
            notifyRecurringDue = prefs[Keys.notifyRecurringDue] ?: true,
            notifyWeeklySummary = prefs[Keys.notifyWeeklySummary] ?: false,
            notifyAwards = prefs[Keys.notifyAwards] ?: true,
            dailyBudgetMinor = prefs[Keys.dailyBudgetMinor] ?: 0L,
            showGreeting = prefs[Keys.showGreeting] ?: true,
            showStreak = prefs[Keys.showStreak] ?: true,
            showRates = prefs[Keys.showRates] ?: true,
            convertOnCurrencyChange = prefs[Keys.convertOnCurrencyChange] ?: true,
            multiAccount = prefs[Keys.multiAccount] ?: false,
            transferFee = prefs[Keys.transferFee] ?: false,

            reportFields = prefs[Keys.reportFields]
                ?: ReportField.entries.map { it.name }.toSet(),
            reportPeriod = prefs[Keys.reportPeriod] ?: DEFAULT_REPORT_PERIOD,
            autoCalculator = prefs[Keys.autoCalculator] ?: true,
            syncPhotos = prefs[Keys.syncPhotos] ?: false
        )
    }

    suspend fun setNotifyDailyReminder(enabled: Boolean) {
        bumped { it[Keys.notifyDailyReminder] = enabled }
    }

    suspend fun setNotifyRecurringDue(enabled: Boolean) {
        bumped { it[Keys.notifyRecurringDue] = enabled }
    }

    suspend fun setNotifyWeeklySummary(enabled: Boolean) {
        bumped { it[Keys.notifyWeeklySummary] = enabled }
    }

    suspend fun setNotifyAwards(enabled: Boolean) {
        bumped { it[Keys.notifyAwards] = enabled }
    }

    suspend fun setCurrencyCode(code: String) {
        bumped { it[Keys.currencyCode] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        bumped { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        bumped { it[Keys.dynamicColors] = enabled }
    }

    private val _language = MutableStateFlow(LocaleHelper.getLanguage(context))

    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        LocaleHelper.setLanguage(context, language)
        _language.value = language
    }

    suspend fun syncSnapshot(): SyncedSettings {
        val prefs = context.dataStore.data.first()
        val current = settings.first()
        val user = profile.first()
        return SyncedSettings(
            updatedAt = prefs[Keys.updatedAt] ?: 0L,
            settings = current,
            profileNickname = user.nickname,
            profileEmoji = user.photoPath?.takeIf {
                it.startsWith(EMOJI_AVATAR_PREFIX)
            }
        )
    }

    suspend fun applySynced(remote: SyncedSettings) {
        val incoming = remote.settings
        context.dataStore.edit { prefs ->
            prefs[Keys.currencyCode] = incoming.currencyCode
            prefs[Keys.themeMode] = incoming.themeMode.name
            prefs[Keys.dynamicColors] = incoming.dynamicColors
            prefs[Keys.notifyDailyReminder] = incoming.notifyDailyReminder
            prefs[Keys.notifyRecurringDue] = incoming.notifyRecurringDue
            prefs[Keys.notifyWeeklySummary] = incoming.notifyWeeklySummary
            prefs[Keys.notifyAwards] = incoming.notifyAwards
            prefs[Keys.dailyBudgetMinor] = incoming.dailyBudgetMinor
            prefs[Keys.showGreeting] = incoming.showGreeting
            prefs[Keys.showStreak] = incoming.showStreak
            prefs[Keys.showRates] = incoming.showRates
            prefs[Keys.convertOnCurrencyChange] = incoming.convertOnCurrencyChange
            prefs[Keys.multiAccount] = incoming.multiAccount
            prefs[Keys.transferFee] = incoming.transferFee
            prefs[Keys.reportFields] = incoming.reportFields
            prefs[Keys.reportPeriod] = incoming.reportPeriod
            prefs[Keys.autoCalculator] = incoming.autoCalculator
            prefs[Keys.syncPhotos] = incoming.syncPhotos
            prefs[Keys.profileNickname] = remote.profileNickname
            prefs.remove(Keys.retiredProfileName)

            val localPhoto = prefs[Keys.profilePhotoPath].orEmpty()
            val localIsPicture = localPhoto.isNotBlank() &&
                !localPhoto.startsWith(EMOJI_AVATAR_PREFIX)
            if (remote.profileEmoji != null && !localIsPicture) {
                prefs[Keys.profilePhotoPath] = remote.profileEmoji
            }
            prefs[Keys.updatedAt] = remote.updatedAt
        }
    }

    companion object {
        const val DEFAULT_CURRENCY = "BYN"
        const val DEFAULT_REPORT_PERIOD = "MONTH"
    }
}
