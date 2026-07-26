package by.mlastovsky.kosht.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ThemeMode
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
    /** Tell the user in the shade when an award is earned. */
    val notifyAwards: Boolean,
    /** Daily spending budget for the streak; 0 = auto from last month. */
    val dailyBudgetMinor: Long,
    val showGreeting: Boolean,
    val showStreak: Boolean,
    val showRates: Boolean,
    /** Recalculate stored amounts by the NBRB rate when switching currency. */
    val convertOnCurrencyChange: Boolean,
    /** Master switch for multiple money sources (cards/cash). */
    val multiAccount: Boolean,
    /** Names of [by.mlastovsky.kosht.model.ReportField] rows shown in the report. */
    val reportFields: Set<String>,
    /** Name of the [by.mlastovsky.kosht.ui.stats.ReportPeriod] the report covers. */
    val reportPeriod: String,
    /** Open the calculator automatically when adding a new record. */
    val autoCalculator: Boolean,
    /**
     * Upload receipt photos to the account as well as the figures. Off unless
     * asked for: the images are the most revealing thing the app holds, and
     * the privacy policy treats switching this on as a consent of its own.
     */
    val syncPhotos: Boolean
)

/** The settings and profile as they travel between a person's devices. */
data class SyncedSettings(
    /** Epoch millis of the last change on the device it came from. */
    val updatedAt: Long,
    val settings: AppSettings,
    val profileName: String,
    val profileNickname: String,
    /** Built-in avatar, e.g. "emoji:🦊"; null when a photo or nothing is set. */
    val profileEmoji: String?
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

    /**
     * Whether Android has already been asked to allow notifications. One of
     * the reminders is on out of the box, so without asking once at the start
     * it would be switched on and silently never arrive.
     */
    val notificationsAsked: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.notificationsAsked] ?: false }

    suspend fun markNotificationsAsked() {
        context.dataStore.edit { it[Keys.notificationsAsked] = true }
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
        val profileName = stringPreferencesKey("profile_name")
        val profileNickname = stringPreferencesKey("profile_nickname")
        val profilePhotoPath = stringPreferencesKey("profile_photo_path")
        val dailyBudgetMinor = longPreferencesKey("daily_budget_minor")
        val showGreeting = booleanPreferencesKey("show_greeting")
        val showStreak = booleanPreferencesKey("show_streak")
        val showRates = booleanPreferencesKey("show_rates")
        val convertOnCurrencyChange = booleanPreferencesKey("convert_on_currency_change")
        val multiAccount = booleanPreferencesKey("multi_account")
        val reportFields = stringSetPreferencesKey("report_fields")
        val reportPeriod = stringPreferencesKey("report_period")
        val autoCalculator = booleanPreferencesKey("auto_calculator")
        val syncPhotos = booleanPreferencesKey("sync_photos")

        /**
         * When any of the settings above last changed, epoch millis. Room's
         * tables get this from triggers; DataStore has no such thing, so every
         * setter goes through [bumped] and stamps it here.
         */
        val updatedAt = longPreferencesKey("settings_updated_at")
    }

    /**
     * Writes preferences and records when it happened, which is what lets the
     * newer side win when two devices have both been fiddling with settings.
     */
    private suspend fun bumped(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
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
            name = prefs[Keys.profileName] ?: "",
            nickname = prefs[Keys.profileNickname] ?: "",
            photoPath = prefs[Keys.profilePhotoPath]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setProfile(name: String, nickname: String) {
        bumped {
            it[Keys.profileName] = name.trim().take(40)
            it[Keys.profileNickname] = nickname.trim().take(24)
        }
    }

    suspend fun setProfilePhoto(path: String?) {
        bumped { it[Keys.profilePhotoPath] = path.orEmpty() }
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
            // Absent preference = all rows; an explicit empty set is honored.
            reportFields = prefs[Keys.reportFields]
                ?: by.mlastovsky.kosht.model.ReportField.entries.map { it.name }.toSet(),
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

    /** In-app language override; SYSTEM follows the device locale. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        LocaleHelper.setLanguage(context, language)
        _language.value = language
    }


    /**
     * Everything about the app that belongs to the person rather than to the
     * phone, as one value the sync engine can carry.
     *
     * The language is deliberately absent: it follows the device, and having a
     * second phone silently switch its interface language would be a surprise
     * rather than a convenience. A profile photo is absent for the same reason
     * receipt photos are — a file path means nothing elsewhere — but a
     * built-in emoji avatar is just text, so it travels.
     */
    suspend fun syncSnapshot(): SyncedSettings {
        val prefs = context.dataStore.data.first()
        val current = settings.first()
        val user = profile.first()
        return SyncedSettings(
            updatedAt = prefs[Keys.updatedAt] ?: 0L,
            settings = current,
            profileName = user.name,
            profileNickname = user.nickname,
            profileEmoji = user.photoPath?.takeIf {
                it.startsWith(by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX)
            }
        )
    }

    /**
     * Applies settings that arrived from another device, keeping the remote
     * stamp so this device does not immediately claim the change as its own.
     */
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
            prefs[Keys.reportFields] = incoming.reportFields
            prefs[Keys.reportPeriod] = incoming.reportPeriod
            prefs[Keys.autoCalculator] = incoming.autoCalculator
            prefs[Keys.syncPhotos] = incoming.syncPhotos
            prefs[Keys.profileName] = remote.profileName
            prefs[Keys.profileNickname] = remote.profileNickname
            // A photo on the other phone is not a photo on this one, so only
            // an emoji avatar is taken -- and only over another emoji, never
            // over a picture this device actually has.
            val localPhoto = prefs[Keys.profilePhotoPath].orEmpty()
            val localIsPicture = localPhoto.isNotBlank() &&
                !localPhoto.startsWith(by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX)
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
