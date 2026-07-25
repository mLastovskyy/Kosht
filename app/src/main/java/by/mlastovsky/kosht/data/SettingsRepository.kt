package by.mlastovsky.kosht.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.util.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val currencyCode: String,
    val themeMode: ThemeMode,
    val dynamicColors: Boolean,
    val notifyDailyReminder: Boolean,
    val notifyRecurringDue: Boolean,
    val notifyWeeklySummary: Boolean,
    /** Daily spending budget for the streak; 0 = auto from last month. */
    val dailyBudgetMinor: Long,
    val showGreeting: Boolean,
    val showStreak: Boolean,
    val showRates: Boolean,
    /** Recalculate stored amounts by the NBRB rate when switching currency. */
    val convertOnCurrencyChange: Boolean
)

data class UserProfile(
    val name: String,
    val nickname: String,
    val photoPath: String?
) {
    /** Nickname wins, then name, then the localized default. */
    fun displayName(fallback: String): String =
        nickname.ifBlank { name.ifBlank { fallback } }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val currencyCode = stringPreferencesKey("currency_code")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColors = booleanPreferencesKey("dynamic_colors")
        val notifyDailyReminder = booleanPreferencesKey("notify_daily_reminder")
        val notifyRecurringDue = booleanPreferencesKey("notify_recurring_due")
        val notifyWeeklySummary = booleanPreferencesKey("notify_weekly_summary")
        val profileName = stringPreferencesKey("profile_name")
        val profileNickname = stringPreferencesKey("profile_nickname")
        val profilePhotoPath = stringPreferencesKey("profile_photo_path")
        val dailyBudgetMinor = longPreferencesKey("daily_budget_minor")
        val showGreeting = booleanPreferencesKey("show_greeting")
        val showStreak = booleanPreferencesKey("show_streak")
        val showRates = booleanPreferencesKey("show_rates")
        val convertOnCurrencyChange = booleanPreferencesKey("convert_on_currency_change")
    }

    suspend fun setConvertOnCurrencyChange(value: Boolean) {
        context.dataStore.edit { it[Keys.convertOnCurrencyChange] = value }
    }

    suspend fun setShowGreeting(value: Boolean) {
        context.dataStore.edit { it[Keys.showGreeting] = value }
    }

    suspend fun setShowStreak(value: Boolean) {
        context.dataStore.edit { it[Keys.showStreak] = value }
    }

    suspend fun setShowRates(value: Boolean) {
        context.dataStore.edit { it[Keys.showRates] = value }
    }

    suspend fun setDailyBudgetMinor(value: Long) {
        context.dataStore.edit { it[Keys.dailyBudgetMinor] = value.coerceAtLeast(0) }
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            name = prefs[Keys.profileName] ?: "",
            nickname = prefs[Keys.profileNickname] ?: "",
            photoPath = prefs[Keys.profilePhotoPath]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setProfile(name: String, nickname: String) {
        context.dataStore.edit {
            it[Keys.profileName] = name.trim().take(40)
            it[Keys.profileNickname] = nickname.trim().take(24)
        }
    }

    suspend fun setProfilePhoto(path: String?) {
        context.dataStore.edit { it[Keys.profilePhotoPath] = path.orEmpty() }
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
            dailyBudgetMinor = prefs[Keys.dailyBudgetMinor] ?: 0L,
            showGreeting = prefs[Keys.showGreeting] ?: true,
            showStreak = prefs[Keys.showStreak] ?: true,
            showRates = prefs[Keys.showRates] ?: true,
            convertOnCurrencyChange = prefs[Keys.convertOnCurrencyChange] ?: true
        )
    }

    suspend fun setNotifyDailyReminder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyDailyReminder] = enabled }
    }

    suspend fun setNotifyRecurringDue(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyRecurringDue] = enabled }
    }

    suspend fun setNotifyWeeklySummary(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyWeeklySummary] = enabled }
    }

    suspend fun setCurrencyCode(code: String) {
        context.dataStore.edit { it[Keys.currencyCode] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { it[Keys.dynamicColors] = enabled }
    }

    private val _language = MutableStateFlow(LocaleHelper.getLanguage(context))

    /** In-app language override; SYSTEM follows the device locale. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        LocaleHelper.setLanguage(context, language)
        _language.value = language
    }

    companion object {
        const val DEFAULT_CURRENCY = "BYN"
    }
}
