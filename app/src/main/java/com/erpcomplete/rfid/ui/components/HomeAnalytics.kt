package com.erpcomplete.rfid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.isBenignCancellation
import com.erpcomplete.rfid.util.workspaceContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class HomeAnalyticsSnapshot(
    val putawayOpen: Int = 0,
    val pickOpen: Int = 0,
    val opnameActive: Int = 0,
    val receiptsDraft: Int = 0,
    val syncPending: Int = 0,
    val readerOnline: Boolean = false,
    val lastUpdatedMs: Long? = null,
)

@Composable
fun rememberHomeAnalytics(
    container: AppContainer,
    readerOnline: Boolean,
    syncPending: Int,
    workspaceKey: String = "",
    refreshIntervalMs: Long = 30_000,
): HomeAnalyticsState {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(HomeAnalyticsSnapshot(readerOnline = readerOnline, syncPending = syncPending)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }

    val refresh: () -> Unit = { tick++ }

    LaunchedEffect(readerOnline, syncPending) {
        snapshot = snapshot.copy(readerOnline = readerOnline, syncPending = syncPending)
    }

    LaunchedEffect(workspaceKey) {
        if (workspaceKey.isNotBlank()) {
            tick++
        }
    }

    LaunchedEffect(tick) {
        loading = snapshot.lastUpdatedMs == null
        error = null
        try {
            val next = loadHomeAnalytics(container, readerOnline, syncPending)
            if (!isActive) return@LaunchedEffect
            snapshot = next
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isBenignCancellation()) {
                error = e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.home_analytics_load_error)
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(refreshIntervalMs) {
        while (isActive) {
            delay(refreshIntervalMs)
            tick++
        }
    }

    return HomeAnalyticsState(snapshot, loading, error, refresh)
}

class HomeAnalyticsState(
    val snapshot: HomeAnalyticsSnapshot,
    val loading: Boolean,
    val error: String?,
    val refresh: () -> Unit,
)

private suspend fun loadHomeAnalytics(
    container: AppContainer,
    readerOnline: Boolean,
    syncPending: Int,
): HomeAnalyticsSnapshot = coroutineScope {
    val whId = container.workspaceContext().warehouseId
    val putaway = async {
        val res = container.api.listPutawayTasks(
            status = "pending,in_progress",
            warehouseId = whId,
            perPage = 1,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopeCount(res)
    }
    val pick = async {
        val res = container.api.listPickLists(
            pickStatus = "pending,in_progress",
            warehouseId = whId,
            perPage = 1,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopeCount(res)
    }
    val opname = async {
        val res = container.api.listStockOpnames(
            status = "in_progress,completed",
            warehouseId = whId,
            perPage = 1,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopeCount(res)
    }
    val receipts = async {
        val pending = container.api.listGoodsReceipts(status = "pending", warehouseId = whId, perPage = 1)
        val partial = container.api.listGoodsReceipts(status = "partial", warehouseId = whId, perPage = 1)
        val pendingCount = if (pending.isSuccessful) WorkflowJson.envelopeCount(pending) else 0
        val partialCount = if (partial.isSuccessful) WorkflowJson.envelopeCount(partial) else 0
        pendingCount + partialCount
    }

    HomeAnalyticsSnapshot(
        putawayOpen = putaway.await(),
        pickOpen = pick.await(),
        opnameActive = opname.await(),
        receiptsDraft = receipts.await(),
        syncPending = syncPending,
        readerOnline = readerOnline,
        lastUpdatedMs = System.currentTimeMillis(),
    )
}

@Composable
fun HomeAnalyticsSection(
    state: HomeAnalyticsState,
    onPutawayClick: () -> Unit,
    onPickClick: () -> Unit,
    onOpnameClick: () -> Unit,
    onReceiveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.home_work_queue_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                state.snapshot.lastUpdatedMs?.let { ms ->
                    Text(
                        stringResource(R.string.home_updated_ago, DisplayFormat.timeAgo(context, ms)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        state.error?.let { msg ->
            Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.loading && state.snapshot.lastUpdatedMs == null) {
            HomeMetricsSkeleton()
        } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeMetricCard(
                label = stringResource(R.string.home_metric_putaway),
                value = state.snapshot.putawayOpen,
                hint = stringResource(R.string.home_metric_putaway_hint),
                modifier = Modifier.weight(1f),
                onClick = onPutawayClick,
            )
            HomeMetricCard(
                label = stringResource(R.string.home_metric_pick_lists),
                value = state.snapshot.pickOpen,
                hint = stringResource(R.string.home_metric_pick_hint),
                modifier = Modifier.weight(1f),
                onClick = onPickClick,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeMetricCard(
                label = stringResource(R.string.home_metric_opname),
                value = state.snapshot.opnameActive,
                hint = stringResource(R.string.home_metric_opname_hint),
                modifier = Modifier.weight(1f),
                onClick = onOpnameClick,
            )
            HomeMetricCard(
                label = stringResource(R.string.home_metric_receipts),
                value = state.snapshot.receiptsDraft,
                hint = stringResource(R.string.home_metric_receipts_hint),
                modifier = Modifier.weight(1f),
                onClick = onReceiveClick,
            )
        }
        }
        ErpCard {
            Text(stringResource(R.string.home_session_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        stringResource(
                            if (state.snapshot.readerOnline) R.string.reader_status_online else R.string.reader_status_offline,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.snapshot.readerOnline) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        if (state.snapshot.syncPending > 0) {
                            pluralStringResource(R.plurals.sync_scans_waiting, state.snapshot.syncPending, state.snapshot.syncPending)
                        } else {
                            stringResource(R.string.sync_all_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    (state.snapshot.putawayOpen + state.snapshot.pickOpen + state.snapshot.opnameActive).toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                stringResource(R.string.home_total_open_tasks),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeMetricCard(
    label: String,
    value: Int,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ErpCard(modifier = modifier, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
