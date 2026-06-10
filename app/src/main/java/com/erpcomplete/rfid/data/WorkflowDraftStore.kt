package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.workflowDraftStore by preferencesDataStore("workflow_drafts")

/** Persists in-progress workflow JSON blobs across process death. */
class WorkflowDraftStore(private val context: Context) {

    fun loadBlocking(key: String): String? = runBlocking {
        context.workflowDraftStore.data.first()[prefKey(key)]
    }

    suspend fun save(key: String, json: String) {
        context.workflowDraftStore.edit { prefs ->
            prefs[prefKey(key)] = json
        }
    }

    suspend fun clear(key: String) {
        context.workflowDraftStore.edit { prefs ->
            prefs.remove(prefKey(key))
        }
    }

    private fun prefKey(key: String) = stringPreferencesKey("draft_$key")
}
