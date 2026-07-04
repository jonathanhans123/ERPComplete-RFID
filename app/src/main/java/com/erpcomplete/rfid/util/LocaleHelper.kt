package com.erpcomplete.rfid.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.erpcomplete.rfid.data.LocaleSettingsStore

object LocaleHelper {

    fun apply(context: Context, languageTag: String) {
        val locales = when (languageTag.trim().lowercase()) {
            LocaleSettingsStore.ENGLISH -> LocaleListCompat.forLanguageTags(LocaleSettingsStore.ENGLISH)
            LocaleSettingsStore.INDONESIAN -> LocaleListCompat.forLanguageTags(LocaleSettingsStore.INDONESIAN)
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
