package com.erpcomplete.rfid.data

import com.erpcomplete.rfid.data.remote.ErpApiService
import com.erpcomplete.rfid.sync.NetworkSyncMonitor
import com.erpcomplete.rfid.sync.SyncRepository
import com.erpcomplete.rfid.ui.util.UiStrings
import com.erpcomplete.rfid.util.ApiErrorParser
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Executes workflow mutations online, or queues them when offline.
 */
class WorkflowApiHelper(
    private val api: ErpApiService,
    private val syncRepository: SyncRepository,
    private val networkMonitor: NetworkSyncMonitor,
) {
    private val gson = Gson()

    suspend fun <T> executeOrQueue(
        endpoint: String,
        method: String,
        body: Any,
        idempotencyKey: String? = null,
        online: suspend () -> Response<T>,
    ): Response<T> {
        val onlineNow = networkMonitor.isOnlineNow()
        if (!onlineNow) {
            syncRepository.queueRequest(endpoint, method, gson.toJson(body), idempotencyKey)
            throw IOException(UiStrings.savedOfflineSync())
        }
        val response = online()
        if (!response.isSuccessful && response.code() in 500..599) {
            syncRepository.queueRequest(endpoint, method, gson.toJson(body), idempotencyKey)
            throw IOException(ApiErrorParser.httpMessage(response, authenticated = true))
        }
        return response
    }
}
