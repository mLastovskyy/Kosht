package by.mlastovsky.kosht.util

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import by.mlastovsky.kosht.model.AppLanguage
import java.util.Locale

object LocaleHelper {

    private const val PREFS = "kosht_locale"
    private const val KEY_LANGUAGE = "app_language"

    fun getLanguage(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.fromTag(tag)
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LANGUAGE, language.tag)
        }
    }

    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        val tag = language.tag ?: return context
        val locale = Locale.forLanguageTag(tag)

        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
