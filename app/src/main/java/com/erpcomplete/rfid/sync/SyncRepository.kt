package com.erpcomplete.rfid.sync

import com.erpcomplete.rfid.data.SessionCache
import com.erpcomplete.rfid.data.local.PendingSyncDao
import com.erpcomplete.rfid.data.local.PendingSyncEntity
import com.erpcomplete.rfid.data.remote.ErpApiService
import com.erpcomplete.rfid.data.remote.ReadsRequest
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class SyncRepository(
    private val dao: PendingSyncDao,
    private val api: ErpApiService,
    private val networkMonitor: NetworkSyncMonitor,
    private val baseUrlProvider: () -> String,
    private val sessionCache: SessionCache,
) {
    private val gson = Gson()
    private val flushMutex = Mutex()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _failedItems = MutableStateFlow<List<PendingSyncEntity>>(emptyList())
    val failedItems: StateFlow<List<PendingSyncEntity>> = _failedItems.asStateFlow()

    private var started = false

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true

        scope.launch {
            dao.observeCount().collect { count ->
                _pendingCount.value = count
            }
        }

        scope.launch {
            dao.observeFailed(MAX_ATTEMPTS).collect { failed ->
                _failedItems.value = failed
            }
        }

        scope.launch {
            combine(
                dao.observeCount().distinctUntilChanged(),
                networkMonitor.isOnline.distinctUntilChanged(),
            ) { pending, online -> pending to online }
                .debounce(400)
                .collect { (pending, online) ->
                    if (pending > 0 && online) {
                        flush()
                    }
                }
        }
    }

    suspend fun refreshPendingCount() {
        _pendingCount.value = dao.count()
        _failedItems.value = dao.failed(MAX_ATTEMPTS)
    }

    suspend fun queueReads(sessionId: String?, workflow: String, reads: List<com.erpcomplete.rfid.data.remote.ReadItem>) {
        val body = ReadsRequest(sessionId, workflow, reads)
        queueRequest("rfid/reads", "POST", gson.toJson(body), reads.firstOrNull()?.idempotency_key)
    }

    suspend fun queueRequest(
        endpoint: String,
        method: String,
        bodyJson: String,
        idempotencyKey: String? = null,
    ) {
        dao.insert(
            PendingSyncEntity(
                endpoint = endpoint,
                method = method.uppercase(),
                bodyJson = bodyJson,
                idempotencyKey = idempotencyKey,
            ),
        )
        refreshPendingCount()
        if (networkMonitor.isOnlineNow()) {
            flush()
        }
    }

    suspend fun clearAll() {
        dao.clearAll()
        refreshPendingCount()
    }

    suspend fun discardFailed() {
        dao.failed(MAX_ATTEMPTS).forEach { dao.delete(it.id) }
        refreshPendingCount()
    }

    suspend fun flush(): Int = flushMutex.withLock {
        var flushed = 0
        val items = dao.oldest()
        for (item in items) {
            if (item.attemptCount >= MAX_ATTEMPTS) continue
            val ok = executeItem(item)
            if (ok) {
                dao.delete(item.id)
                flushed++
            } else {
                dao.markFailed(item.id, "HTTP error")
            }
        }
        refreshPendingCount()
        flushed
    }

    private suspend fun executeItem(item: PendingSyncEntity): Boolean {
        return when {
            item.endpoint == "rfid/reads" && item.method == "POST" -> {
                val body = gson.fromJson(item.bodyJson, ReadsRequest::class.java)
                api.ingestReads(body).isSuccessful
            }
            item.method == "POST" -> {
                val url = buildUrl(item.endpoint)
                api.postRaw(url, item.bodyJson.toRequestBody(jsonMedia)).isSuccessful
            }
            item.method == "PUT" -> {
                val url = buildUrl(item.endpoint)
                api.putRaw(url, item.bodyJson.toRequestBody(jsonMedia)).isSuccessful
            }
            else -> false
        }
    }

    private fun buildUrl(endpoint: String): String {
        val base = baseUrlProvider().trimEnd('/')
        val path = endpoint.trimStart('/')
        return "$base/$path"
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
