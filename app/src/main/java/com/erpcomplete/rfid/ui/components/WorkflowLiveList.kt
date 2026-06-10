package com.erpcomplete.rfid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.erpcomplete.rfid.util.isBenignCancellation
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
class WorkflowLiveListState(
    val rows: List<JsonObject>,
    val loading: Boolean,
    val loadingMore: Boolean,
    val error: String?,
    val lastUpdatedMs: Long?,
    val hasMore: Boolean,
    val totalCount: Int?,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
)

@Composable
fun rememberWorkflowLiveList(
    enabled: Boolean,
    intervalMs: Long = 12_000,
    loader: suspend (page: Int) -> WorkflowListPage,
): WorkflowLiveListState {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdatedMs by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var lastPage by remember { mutableStateOf(1) }
    var totalCount by remember { mutableStateOf<Int?>(null) }

    val refresh: () -> Unit = {
        page = 1
        tick++
    }
    val loadMore: () -> Unit = {
        if (!loadingMore && page < lastPage) {
            page++
            tick++
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) loading = false
    }

    LaunchedEffect(enabled, tick, page) {
        if (!enabled) return@LaunchedEffect
        val isLoadMore = page > 1 && rows.isNotEmpty()
        if (isLoadMore) loadingMore = true else if (rows.isEmpty()) loading = true
        try {
            val result = loader(page)
            if (!isActive) return@LaunchedEffect
            rows = if (isLoadMore) rows + result.rows else result.rows
            lastPage = result.lastPage
            totalCount = result.total
            error = null
            lastUpdatedMs = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isActive && !e.isBenignCancellation()) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Failed to load list"
            }
        } finally {
            if (isActive) {
                loading = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(enabled, intervalMs) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            delay(intervalMs)
            if (!isActive) break
            if (page != 1) continue
            try {
                val result = loader(1)
                if (!isActive) break
                rows = result.rows
                lastPage = result.lastPage
                totalCount = result.total
                error = null
                lastUpdatedMs = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive && !e.isBenignCancellation()) {
                    error = e.message?.takeIf { it.isNotBlank() } ?: "Failed to refresh list"
                }
            }
        }
    }

    return WorkflowLiveListState(
        rows = rows,
        loading = loading,
        loadingMore = loadingMore,
        error = error,
        lastUpdatedMs = lastUpdatedMs.takeIf { it > 0 },
        hasMore = page < lastPage,
        totalCount = totalCount,
        refresh = refresh,
        loadMore = loadMore,
    )
}

@Composable
fun LiveSyncIndicator(lastUpdatedMs: Long?, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val label = com.erpcomplete.rfid.util.DisplayFormat.timeAgo(lastUpdatedMs)
    if (label.isBlank()) return
    androidx.compose.material3.Text(
        text = "Synced $label",
        modifier = modifier,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
