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

    /*
     * Each save sets exactly the fields it wrote, from the values it wrote. Re-reading everything
     * from DataStore after an unrelated save (e.g. picking a warehouse) could land mid-refresh and
     * put an already-revoked token back into memory, which then signed the user out.
     */
    fun updateToken(token: String?) {
        accessToken = token
    }

    fun setWorkspace(businessUnitId: String, teamId: String, warehouseId: String) {
        this.businessUnitId = businessUnitId
        this.teamId = teamId
        this.warehouseId = warehouseId
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
