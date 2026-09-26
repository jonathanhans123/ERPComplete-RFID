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
        val response = try {
            online()
        } catch (e: IOException) {
            // Never reached the server (dropped connection, timeout): safe to replay later.
            syncRepository.queueRequest(endpoint, method, gson.toJson(body), idempotencyKey)
            throw IOException(UiStrings.savedOfflineSync(), e)
        }
        // Only gateway/unavailable errors mean the ERP app never handled the request. A 500 is a
        // real answer (e.g. a validation or posting failure): replaying it just repeats the
        // failure, and replaying non-idempotent steps like "complete picking" races the retry.
        if (response.code() in RETRYABLE_STATUS) {
            syncRepository.queueRequest(endpoint, method, gson.toJson(body), idempotencyKey)
            throw IOException(ApiErrorParser.httpMessage(response, authenticated = true))
        }
        return response
    }

    private companion object {
        val RETRYABLE_STATUS = setOf(502, 503, 504)
    }
}
