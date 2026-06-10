package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.rfidSettingsDataStore by preferencesDataStore("rfid_settings")

enum class ScanProfile {
    RANGE,
    DENSE,
}

class RfidSettingsStore(private val context: Context) {

    val scanProfile: Flow<ScanProfile> = context.rfidSettingsDataStore.data.map { prefs ->
        if (prefs[KEY_SCAN_PROFILE] == "dense") ScanProfile.DENSE else ScanProfile.RANGE
    }

    val epcPrefilterEnabled: Flow<Boolean> = context.rfidSettingsDataStore.data.map { prefs ->
        prefs[KEY_EPC_PREFILTER] ?: true
    }

    val epcCompanyPrefix: Flow<String?> = context.rfidSettingsDataStore.data.map { prefs ->
        prefs[KEY_EPC_PREFIX]?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    }

    val autoFirmwareCheck: Flow<Boolean> = context.rfidSettingsDataStore.data.map { prefs ->
        prefs[KEY_AUTO_FIRMWARE_CHECK] ?: true
    }

    suspend fun setScanProfile(profile: ScanProfile) {
        context.rfidSettingsDataStore.edit { prefs ->
            prefs[KEY_SCAN_PROFILE] = if (profile == ScanProfile.DENSE) "dense" else "range"
        }
    }

    suspend fun setEpcPrefilterEnabled(enabled: Boolean) {
        context.rfidSettingsDataStore.edit { prefs ->
            prefs[KEY_EPC_PREFILTER] = enabled
        }
    }

    suspend fun setAutoFirmwareCheck(enabled: Boolean) {
        context.rfidSettingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_FIRMWARE_CHECK] = enabled
        }
    }

    suspend fun markFirmwareCheckedNow() {
        context.rfidSettingsDataStore.edit { prefs ->
            prefs[KEY_LAST_FIRMWARE_CHECK_AT] = System.currentTimeMillis()
        }
    }

    suspend fun lastFirmwareCheckAt(): Long? {
        val value = context.rfidSettingsDataStore.data.first()[KEY_LAST_FIRMWARE_CHECK_AT]
        return value?.takeIf { it > 0L }
    }

    suspend fun setEpcCompanyPrefix(prefix: String?) {
        val normalized = prefix?.trim()?.uppercase()?.replace(Regex("[^0-9A-F]"), "")?.takeIf { it.isNotBlank() }
        context.rfidSettingsDataStore.edit { prefs ->
            if (normalized == null) {
                prefs.remove(KEY_EPC_PREFIX)
            } else {
                prefs[KEY_EPC_PREFIX] = normalized
            }
        }
    }

    fun scanProfileBlocking(): ScanProfile = runBlocking {
        scanProfile.first()
    }

    fun epcPrefilterEnabledBlocking(): Boolean = runBlocking {
        epcPrefilterEnabled.first()
    }

    fun epcCompanyPrefixBlocking(): String? = runBlocking {
        epcCompanyPrefix.first()
    }

    fun autoFirmwareCheckBlocking(): Boolean = runBlocking {
        autoFirmwareCheck.first()
    }

    companion object {
        private val KEY_SCAN_PROFILE = stringPreferencesKey("scan_profile")
        private val KEY_EPC_PREFILTER = booleanPreferencesKey("epc_prefilter_enabled")
        private val KEY_EPC_PREFIX = stringPreferencesKey("epc_company_prefix")
        private val KEY_AUTO_FIRMWARE_CHECK = booleanPreferencesKey("auto_firmware_check")
        private val KEY_LAST_FIRMWARE_CHECK_AT = longPreferencesKey("last_firmware_check_at")
    }
}
