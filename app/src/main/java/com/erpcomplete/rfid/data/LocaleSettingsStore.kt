package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.localeSettingsStore by preferencesDataStore("locale_settings")

class LocaleSettingsStore(private val context: Context) {

    val languageTag: Flow<String> = context.localeSettingsStore.data.map { prefs ->
        prefs[KEY_LANGUAGE_TAG].orEmpty()
    }

    fun languageTagBlocking(): String = runBlocking {
        languageTag.first()
    }

    suspend fun setLanguageTag(tag: String) {
        val normalized = tag.trim().lowercase()
        context.localeSettingsStore.edit { prefs ->
            if (normalized.isBlank()) {
                prefs.remove(KEY_LANGUAGE_TAG)
            } else {
                prefs[KEY_LANGUAGE_TAG] = normalized
            }
        }
    }

    companion object {
        private val KEY_LANGUAGE_TAG = stringPreferencesKey("language_tag")

        /** Follow the device system language. */
        const val SYSTEM = ""

        const val ENGLISH = "en"

        /** Indonesian (Android legacy tag `in`). */
        const val INDONESIAN = "in"

        val SUPPORTED_TAGS = listOf(SYSTEM, ENGLISH, INDONESIAN)
    }
}
