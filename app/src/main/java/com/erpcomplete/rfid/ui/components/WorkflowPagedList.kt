package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.util.isBenignCancellation
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * One server page at a time (unlike [rememberWorkflowLiveList], which appends pages). For lists
 * that can hold thousands of rows — e.g. stock at a location — where loading everything is too
 * heavy: the server filters ([queryKey] changes restart at page 1) and the UI pages through.
 * The current page also refreshes every [intervalMs].
 */
@Stable
class WorkflowPagedListState(
    val rows: List<JsonObject>,
    val page: Int,
    val lastPage: Int,
    val total: Int?,
    val loading: Boolean,
    val error: String?,
    val lastUpdatedMs: Long?,
    val goTo: (Int) -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun rememberWorkflowPagedList(
    enabled: Boolean,
    queryKey: Any?,
    intervalMs: Long = 12_000,
    loader: suspend (page: Int) -> WorkflowListPage,
): WorkflowPagedListState {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // Keyed on the query: a new search/filter starts from the first page (no stale-page load).
    var page by remember(queryKey) { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdatedMs by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }

    suspend fun loadCurrent(showSpinner: Boolean) {
        if (showSpinner) loading = true
        try {
            val result = loader(page)
            rows = result.rows
            lastPage = maxOf(1, result.lastPage)
            total = result.total
            if (page > lastPage) page = lastPage // shrank since last load: land on the last page
            error = null
            lastUpdatedMs = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isBenignCancellation()) {
                error = e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.list_load_error)
            }
        } finally {
            if (showSpinner) loading = false
        }
    }

    LaunchedEffect(enabled, queryKey, page, tick) {
        if (enabled) loadCurrent(showSpinner = true)
    }

    LaunchedEffect(enabled, queryKey, page) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            delay(intervalMs)
            if (!isActive) break
            loadCurrent(showSpinner = false)
        }
    }

    return WorkflowPagedListState(
        rows = rows,
        page = page,
        lastPage = lastPage,
        total = total,
        loading = loading,
        error = error,
        lastUpdatedMs = lastUpdatedMs.takeIf { it > 0 },
        goTo = { target -> page = target.coerceIn(1, lastPage) },
        refresh = { tick++ },
    )
}

/** Previous / "Page x of y · N lines" / Next. Hidden when everything fits on one page. */
@Composable
fun WorkflowPager(state: WorkflowPagedListState, modifier: Modifier = Modifier) {
    if (state.lastPage <= 1) return
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { state.goTo(state.page - 1) },
            enabled = !state.loading && state.page > 1,
        ) { Text(stringResource(R.string.pager_previous)) }
        Text(
            buildString {
                append(stringResource(R.string.pager_page_of, state.page, state.lastPage))
                state.total?.let { append(" · ").append(stringResource(R.string.pager_lines, it)) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { state.goTo(state.page + 1) },
            enabled = !state.loading && state.page < state.lastPage,
        ) { Text(stringResource(R.string.pager_next)) }
    }
}
