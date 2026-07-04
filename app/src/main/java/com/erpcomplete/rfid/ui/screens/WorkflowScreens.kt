package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.ReadItem
import com.erpcomplete.rfid.data.remote.StartSessionRequest
import com.erpcomplete.rfid.data.remote.SubmitSessionRequest
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.WorkflowListTable
import com.erpcomplete.rfid.ui.components.WorkflowScanSection
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.StatusMessage
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CycleCountScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var locationId by remember { mutableStateOf("") }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val tags by container.rfidManager.scannedTags.collectAsState()
    val scope = rememberCoroutineScope()
    WorkflowShell(
        title = stringResource(R.string.cycle_count_title),
        subtitle = stringResource(R.string.cycle_count_subtitle),
        tags = tags,
        container = container,
        message = message,
        onBack = onBack,
    ) {
        OutlinedTextField(
            locationId,
            { locationId = it },
            label = { Text(stringResource(R.string.cycle_count_label_location)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ErpPrimaryButton(text = stringResource(R.string.cycle_count_btn_start_session), onClick = {
            scope.launch {
                runCatching {
                    val res = container.api.startSession(StartSessionRequest(locationId.toLongOrNull()))
                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                    sessionId = res.body()?.data?.session?.get("session_uuid")?.toString()
                    res.body()?.message ?: context.getString(R.string.cycle_count_success_session_started)
                }.onSuccess { message = it }.onFailure { message = it.message }
            }
        })
        WorkflowListTable(
            columns = listOf(
                stringResource(R.string.cycle_count_col_epc) to 2f,
                stringResource(R.string.cycle_count_col_rssi) to 0.5f,
            ),
            rows = tags.map { listOf(it.epc, it.rssi?.toString() ?: stringResource(R.string.display_empty)) },
            emptyText = stringResource(R.string.cycle_count_empty_tags),
        )
        ErpPrimaryButton(text = stringResource(R.string.cycle_count_btn_submit), onClick = {
            scope.launch {
                runCatching {
                    val sid = sessionId ?: error(context.getString(R.string.cycle_count_error_start_session_first))
                    val epcs = tags.map { it.epc }
                    val res = container.api.submitSession(sid, SubmitSessionRequest(epcs))
                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                    queueReads(container, "cycle_count", epcs)
                    res.body()?.message ?: context.getString(R.string.cycle_count_success_submitted)
                }.onSuccess { message = it }.onFailure { message = it.message }
            }
        })
    }
}

@Composable
private fun WorkflowShell(
    title: String,
    subtitle: String,
    tags: List<RfidManager.ScannedTag>,
    container: AppContainer,
    message: String?,
    onBack: () -> Unit,
    fields: @Composable () -> Unit,
) {
    ErpScaffold(title = title, subtitle = subtitle, onBack = onBack) {
        message?.let { StatusBanner(it, isError = StatusMessage.looksLikeError(it)) }
        WorkflowScanSection(container, tags, onClear = { container.rfidManager.clearScannedTags() })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fields()
        }
    }
}

internal suspend fun queueReads(container: AppContainer, workflow: String, epcs: List<String>) {
    if (epcs.isEmpty()) return
    val sessionId = UUID.randomUUID().toString()
    val reads = epcs.map { ReadItem(it, idempotency_key = "$sessionId-$it") }
    container.syncRepository.queueReads(sessionId, workflow, reads)
}
