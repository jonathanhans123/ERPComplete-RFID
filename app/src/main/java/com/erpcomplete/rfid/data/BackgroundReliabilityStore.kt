package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.backgroundReliabilityDataStore by preferencesDataStore("background_reliability")

class BackgroundReliabilityStore(private val context: Context) {

    suspend fun shouldPrompt(): Boolean {
        val prefs = context.backgroundReliabilityDataStore.data.first()
        if (prefs[KEY_DONT_ASK] == true) return false
        val snoozeUntil = prefs[KEY_SNOOZE_UNTIL] ?: 0L
        if (System.currentTimeMillis() < snoozeUntil) return false
        return true
    }

    fun shouldPromptBlocking(): Boolean = runBlocking { shouldPrompt() }

    suspend fun snooze(hours: Long = 24) {
        context.backgroundReliabilityDataStore.edit { prefs ->
            prefs[KEY_SNOOZE_UNTIL] = System.currentTimeMillis() + hours * 60L * 60L * 1000L
        }
    }

    suspend fun setDontAskAgain(value: Boolean) {
        context.backgroundReliabilityDataStore.edit { prefs ->
            if (value) {
                prefs[KEY_DONT_ASK] = true
            } else {
                prefs.remove(KEY_DONT_ASK)
            }
        }
    }

    companion object {
        private val KEY_SNOOZE_UNTIL = longPreferencesKey("snooze_until_ms")
        private val KEY_DONT_ASK = booleanPreferencesKey("dont_ask")
    }
}
