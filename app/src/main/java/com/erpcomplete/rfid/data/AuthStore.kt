package com.erpcomplete.rfid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erpcomplete.rfid.data.model.BusinessUnitOption
import com.erpcomplete.rfid.data.model.WorkspaceOption
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("auth")

data class SessionSnapshot(
    val loggedIn: Boolean,
    val hasWorkspace: Boolean,
)

class AuthStore(private val context: Context) {

    private val gson = Gson()
    private var sessionCache: SessionCache? = null

    fun attachSessionCache(cache: SessionCache) {
        sessionCache = cache
    }

    private fun notifySessionCache() {
        sessionCache?.refreshFromStore(this)
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_NAME] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[KEY_EMAIL] }
    val businessUnitId: Flow<String?> = context.dataStore.data.map { it[KEY_BU] }
    val businessUnitName: Flow<String?> = context.dataStore.data.map { it[KEY_BU_NAME] }
    val teamId: Flow<String?> = context.dataStore.data.map { it[KEY_TEAM] }
    val teamName: Flow<String?> = context.dataStore.data.map { it[KEY_TEAM_NAME] }
    val warehouseId: Flow<String?> = context.dataStore.data.map { it[KEY_WAREHOUSE] }
    val warehouseName: Flow<String?> = context.dataStore.data.map { it[KEY_WAREHOUSE_NAME] }

    val isLoggedIn: Flow<Boolean> = accessToken.map { !it.isNullOrBlank() }

    val hasWorkspaceSelected: Flow<Boolean> = combine(
        businessUnitId,
        teamId,
        warehouseId,
    ) { bu, team, warehouse ->
        !bu.isNullOrBlank() && !team.isNullOrBlank() && !warehouse.isNullOrBlank()
    }

    suspend fun saveLogin(
        token: String,
        email: String,
        name: String?,
        businessUnits: List<BusinessUnitOption>,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_EMAIL] = email
            if (!name.isNullOrBlank()) prefs[KEY_NAME] = name
            prefs[KEY_BU_LIST] = gson.toJson(businessUnits)
            prefs.remove(KEY_BU)
            prefs.remove(KEY_BU_NAME)
            prefs.remove(KEY_TEAM)
            prefs.remove(KEY_TEAM_NAME)
            prefs.remove(KEY_WAREHOUSE)
            prefs.remove(KEY_WAREHOUSE_NAME)
            if (!prefs.contains(KEY_DEVICE)) {
                prefs[KEY_DEVICE] = AppContainer.newDeviceUuid()
            }
        }
        notifySessionCache()
    }

    suspend fun updateAccessToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
        }
        notifySessionCache()
    }

    suspend fun saveWorkspace(workspace: WorkspaceOption) {
        val teamId = workspace.teamId
            ?: error("Warehouse ${workspace.warehouseName} has no team — contact your administrator.")
        context.dataStore.edit { prefs ->
            prefs[KEY_WAREHOUSE] = workspace.warehouseId.toString()
            prefs[KEY_WAREHOUSE_NAME] = workspace.warehouseName
            prefs[KEY_BU] = workspace.businessUnitId.toString()
            prefs[KEY_BU_NAME] = workspace.businessUnitName
            prefs[KEY_TEAM] = teamId.toString()
            workspace.teamName?.takeIf { it.isNotBlank() }?.let { prefs[KEY_TEAM_NAME] = it }
        }
        notifySessionCache()
    }

    suspend fun getBusinessUnits(): List<BusinessUnitOption> {
        val json = context.dataStore.data.first()[KEY_BU_LIST] ?: return emptyList()
        val type = object : TypeToken<List<BusinessUnitOption>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun clearWorkspace() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_BU)
            prefs.remove(KEY_BU_NAME)
            prefs.remove(KEY_TEAM)
            prefs.remove(KEY_TEAM_NAME)
            prefs.remove(KEY_WAREHOUSE)
            prefs.remove(KEY_WAREHOUSE_NAME)
        }
        notifySessionCache()
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            val device = prefs[KEY_DEVICE]
            prefs.clear()
            device?.let { prefs[KEY_DEVICE] = it }
        }
        notifySessionCache()
    }

    fun accessTokenBlocking(): String? = runBlocking { accessToken.first() }
    fun businessUnitIdBlocking(): String? = runBlocking { businessUnitId.first() }
    fun teamIdBlocking(): String? = runBlocking { teamId.first() }
    fun warehouseIdBlocking(): String? = runBlocking { warehouseId.first() }
    fun warehouseNameBlocking(): String? = runBlocking { warehouseName.first() }

    /** Synchronous read for cold start — avoids showing login before DataStore emits. */
    fun readSessionSnapshot(): SessionSnapshot {
        val token = accessTokenBlocking()
        val bu = businessUnitIdBlocking()
        val team = teamIdBlocking()
        val warehouse = warehouseIdBlocking()
        return SessionSnapshot(
            loggedIn = !token.isNullOrBlank(),
            hasWorkspace = !bu.isNullOrBlank() && !team.isNullOrBlank() && !warehouse.isNullOrBlank(),
        )
    }

    fun deviceUuidBlocking(): String = runBlocking {
        val prefs = context.dataStore.data.first()
        prefs[KEY_DEVICE] ?: AppContainer.newDeviceUuid().also { uuid ->
            context.dataStore.edit { it[KEY_DEVICE] = uuid }
        }
    }

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("access_token")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_NAME = stringPreferencesKey("user_name")
        private val KEY_BU = stringPreferencesKey("business_unit_id")
        private val KEY_BU_NAME = stringPreferencesKey("business_unit_name")
        private val KEY_TEAM = stringPreferencesKey("team_id")
        private val KEY_TEAM_NAME = stringPreferencesKey("team_name")
        private val KEY_WAREHOUSE = stringPreferencesKey("warehouse_id")
        private val KEY_WAREHOUSE_NAME = stringPreferencesKey("warehouse_name")
        private val KEY_BU_LIST = stringPreferencesKey("business_units_json")
        private val KEY_DEVICE = stringPreferencesKey("device_uuid")
    }
}
