package com.erpcomplete.rfid.data

/**
 * In-memory session headers — avoids [runBlocking] DataStore reads on every HTTP request.
 * Updated when [AuthStore] saves login, workspace, or clear.
 */
class SessionCache(authStore: AuthStore) {
    @Volatile var accessToken: String? = authStore.accessTokenBlocking()
        private set
    @Volatile var businessUnitId: String? = authStore.businessUnitIdBlocking()
        private set
    @Volatile var teamId: String? = authStore.teamIdBlocking()
        private set
    @Volatile var warehouseId: String? = authStore.warehouseIdBlocking()
        private set

    fun refreshFromStore(authStore: AuthStore) {
        accessToken = authStore.accessTokenBlocking()
        businessUnitId = authStore.businessUnitIdBlocking()
        teamId = authStore.teamIdBlocking()
        warehouseId = authStore.warehouseIdBlocking()
    }

    fun updateToken(token: String?) {
        accessToken = token
    }

    fun clearWorkspace() {
        businessUnitId = null
        teamId = null
        warehouseId = null
    }

    fun clearAll() {
        accessToken = null
        clearWorkspace()
    }
}
