package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.readerDataStore by preferencesDataStore("rfid_reader")

data class SavedReader(
    val name: String,
    val address: String,
)

class ReaderStore(private val context: Context) {

    suspend fun saveReader(name: String?, address: String?) {
        if (name.isNullOrBlank() || address.isNullOrBlank()) return
        context.readerDataStore.edit { prefs ->
            prefs[KEY_READER_NAME] = name
            prefs[KEY_READER_ADDRESS] = address
        }
    }

    suspend fun getSavedReader(): SavedReader? {
        val prefs = context.readerDataStore.data.first()
        val name = prefs[KEY_READER_NAME] ?: return null
        val address = prefs[KEY_READER_ADDRESS] ?: return null
        return SavedReader(name, address)
    }

    fun getSavedReaderBlocking(): SavedReader? = runBlocking { getSavedReader() }

    suspend fun savePhoneMac(mac: String) {
        val normalized = mac.replace(":", "").replace("-", "").uppercase().takeIf { it.length == 12 }
            ?: return
        context.readerDataStore.edit { prefs ->
            prefs[KEY_PHONE_MAC] = normalized
        }
    }

    suspend fun getPhoneMac(): String? =
        context.readerDataStore.data.map { it[KEY_PHONE_MAC] }.first()

    fun getPhoneMacBlocking(): String? = runBlocking {
        context.readerDataStore.data.first()[KEY_PHONE_MAC]
    }

    suspend fun clearSavedReader() {
        context.readerDataStore.edit { prefs ->
            prefs.remove(KEY_READER_NAME)
            prefs.remove(KEY_READER_ADDRESS)
        }
    }

    /** Clears reader pairing only — phone Bluetooth address is kept for the Show phone barcode tab. */
    suspend fun clear() {
        clearSavedReader()
    }

    companion object {
        private val KEY_READER_NAME = stringPreferencesKey("reader_name")
        private val KEY_READER_ADDRESS = stringPreferencesKey("reader_address")
        private val KEY_PHONE_MAC = stringPreferencesKey("phone_bt_mac")
    }
}
