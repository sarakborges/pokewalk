package io.github.sarakborges.litewalker

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppPreferences {
    const val LANGUAGE_PT_BR = "pt-BR"
    const val LANGUAGE_EN_US = "en-US"
    const val CARD_CONFIGURATION = "configuration"
    const val CARD_CURRENT_WORKOUT = "current_workout"
    const val CARD_HISTORY = "history"

    private const val PREFS = "litewalker_preferences"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CARD_EXPANDED_PREFIX = "card_expanded_"

    fun isDarkMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun languageTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, LANGUAGE_PT_BR)
            ?.takeIf { it == LANGUAGE_PT_BR || it == LANGUAGE_EN_US }
            ?: LANGUAGE_PT_BR

    fun setLanguageTag(context: Context, languageTag: String) {
        val supported = if (languageTag == LANGUAGE_EN_US) {
            LANGUAGE_EN_US
        } else {
            LANGUAGE_PT_BR
        }
        prefs(context).edit().putString(KEY_LANGUAGE, supported).apply()
    }

    fun isCardExpanded(context: Context, card: String): Boolean =
        prefs(context).getBoolean(KEY_CARD_EXPANDED_PREFIX + card, true)

    fun setCardExpanded(context: Context, card: String, expanded: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CARD_EXPANDED_PREFIX + card, expanded)
            .apply()
    }

    fun localizedContext(context: Context): Context {
        val locale = Locale.forLanguageTag(languageTag(context))
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
