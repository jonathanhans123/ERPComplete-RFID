package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erpcomplete.rfid.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.apiSettingsStore by preferencesDataStore("api_settings")

class ApiSettingsStore(private val context: Context) {

    val baseUrl: Flow<String> = context.apiSettingsStore.data.map { prefs ->
        prefs[KEY_API_BASE_URL]?.takeIf { it.isNotBlank() } ?: BuildConfig.API_BASE_URL
    }

    fun baseUrlBlocking(): String = runBlocking {
        baseUrl.first()
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        context.apiSettingsStore.edit { prefs ->
            if (normalized.isBlank() || normalized == BuildConfig.API_BASE_URL.trimEnd('/')) {
                prefs.remove(KEY_API_BASE_URL)
            } else {
                prefs[KEY_API_BASE_URL] = normalized
            }
        }
    }

    companion object {
        private val KEY_API_BASE_URL = stringPreferencesKey("api_base_url")
    }
}
