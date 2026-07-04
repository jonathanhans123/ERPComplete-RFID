package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.taskNotificationDataStore by preferencesDataStore("task_notifications")

class NotificationSettingsStore(private val context: Context) {

    val taskAlertsEnabled: Flow<Boolean> = context.taskNotificationDataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: true
    }

    suspend fun setTaskAlertsEnabled(enabled: Boolean) {
        context.taskNotificationDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    fun taskAlertsEnabledBlocking(): Boolean = runBlocking {
        taskAlertsEnabled.first()
    }

    suspend fun readLastCounts(): TaskCounts = context.taskNotificationDataStore.data.first().let { prefs ->
        TaskCounts(
            receipts = prefs[KEY_LAST_RECEIPTS] ?: 0,
            putaway = prefs[KEY_LAST_PUTAWAY] ?: 0,
            pick = prefs[KEY_LAST_PICK] ?: 0,
        )
    }

    suspend fun writeLastCounts(counts: TaskCounts) {
        context.taskNotificationDataStore.edit { prefs ->
            prefs[KEY_LAST_RECEIPTS] = counts.receipts
            prefs[KEY_LAST_PUTAWAY] = counts.putaway
            prefs[KEY_LAST_PICK] = counts.pick
        }
    }

    data class TaskCounts(val receipts: Int = 0, val putaway: Int = 0, val pick: Int = 0)

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("task_alerts_enabled")
        private val KEY_LAST_RECEIPTS = intPreferencesKey("last_receipts")
        private val KEY_LAST_PUTAWAY = intPreferencesKey("last_putaway")
        private val KEY_LAST_PICK = intPreferencesKey("last_pick")
    }
}
