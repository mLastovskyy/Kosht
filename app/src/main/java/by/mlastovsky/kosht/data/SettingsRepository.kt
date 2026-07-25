package by.mlastovsky.kosht.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val dynamicColors: Boolean
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val currencyCode = stringPreferencesKey("currency_code")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColors = booleanPreferencesKey("dynamic_colors")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            currencyCode = prefs[Keys.currencyCode] ?: DEFAULT_CURRENCY,
            themeMode = prefs[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColors = prefs[Keys.dynamicColors] ?: false
        )
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
