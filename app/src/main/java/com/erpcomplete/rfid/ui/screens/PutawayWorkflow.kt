package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.PutawayConfirmRequest
import com.erpcomplete.rfid.data.remote.PutawayItemUpdate
import com.erpcomplete.rfid.data.remote.PutawayLocationRow
import com.erpcomplete.rfid.data.remote.UpdatePutawayRequest
import com.erpcomplete.rfid.ui.components.DataTableColumn
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.QtyField
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.IndexColumnSpec
import com.erpcomplete.rfid.ui.components.JsonIndexListTable
import com.erpcomplete.rfid.ui.components.PutawayDetailSkeleton
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.TableCell
import com.erpcomplete.rfid.ui.components.WorkflowDataTable
import com.erpcomplete.rfid.ui.components.ScanMatchStatus
import com.erpcomplete.rfid.ui.components.WorkflowLineScanSection
import com.erpcomplete.rfid.ui.components.WorkflowMatchLine
import com.erpcomplete.rfid.ui.components.incrementQtyField
import com.erpcomplete.rfid.ui.components.scanIncrementDelta
import com.erpcomplete.rfid.ui.components.rememberScanMatchColors
import com.erpcomplete.rfid.ui.components.rememberWorkflowLiveList
import com.erpcomplete.rfid.ui.components.rememberTableSortSearch
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.StatusMessage
import com.google.gson.Gson
import kotlinx.coroutines.delay
import com.erpcomplete.rfid.ui.util.UiStrings
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.PickerMappers
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.isBenignCancellationMessage
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.workspaceContext
import com.erpcomplete.rfid.util.WorkflowJson.array
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.nestedItems
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.rollDisplayLabel
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.google.gson.JsonObject
import java.util.UUID

private sealed class PutawayStep {
    data object List : PutawayStep()
    data class Detail(val id: Long) : PutawayStep()
}

private fun PutawayStep.encode(): String = when (this) {
    PutawayStep.List -> "list"
    is PutawayStep.Detail -> "detail:$id"
}

private fun decodePutawayStep(raw: String): PutawayStep {
    val parts = raw.split(':', limit = 2)
    return when (parts[0]) {
        "detail" -> PutawayStep.Detail(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        else -> PutawayStep.List
    }
}

private val PutawayStepSaver = androidx.compose.runtime.saveable.Saver<PutawayStep, String>(
    save = { it.encode() },
    restore = { decodePutawayStep(it) },
)

private data class PutawayLineEdit(
    val itemId: Long,
    val productId: Long,
    val variationValueId: Long?,
    val rollNumber: String?,
    val productLabel: String,
    val variationLabel: String,
    val rollLabel: String,
    val isRoll: Boolean,
    val quantityUnitSuffix: String?,
    val qtyToPutaway: Double,
    var locationId: Long?,
    var locationLabel: String,
    var qtyPutaway: String,
    val existingLocationRowId: Long?,
)

@Composable
fun PutawayScreen(
    container: AppContainer,
    initialTaskId: Long? = null,
    onBack: () -> Unit,
) {
    var step by rememberSaveable(stateSaver = PutawayStepSaver) { mutableStateOf(PutawayStep.List) }
    var message by remember { mutableStateOf<String?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    val tags by container.rfidManager.scannedTags.collectAsState()
    val scope = rememberCoroutineScope()
    val scanColors = rememberScanMatchColors()

    val putawayTitle = stringResource(R.string.putaway_title)
    val putawaySubtitleList = stringResource(R.string.putaway_subtitle_list)
    val errEmptyTask = stringResource(R.string.putaway_error_empty_task)
    val errScanTagsFirst = stringResource(R.string.common_error_scan_tags_first)
    val msgConfirmed = stringResource(R.string.common_success_confirmed)
    val msgPutawaySaved = stringResource(R.string.putaway_success_saved)
    val msgPutawayCompleted = stringResource(R.string.putaway_success_completed)
    val colProduct = stringResource(R.string.common_col_product)
    val colVar = stringResource(R.string.common_col_variation)
    val colRoll = stringResource(R.string.common_col_roll)
    val colToPut = stringResource(R.string.putaway_col_to_put)
    val colLocation = stringResource(R.string.label_location)
    val colPut = stringResource(R.string.putaway_col_put)
    val labelPutawayLength = stringResource(R.string.putaway_label_putaway_length)
    val labelQtyPutaway = stringResource(R.string.putaway_label_qty_putaway)
    val rollMarker = stringResource(R.string.putaway_line_roll_marker)

    var task by remember { mutableStateOf<JsonObject?>(null) }
    var taskLoading by remember { mutableStateOf(false) }
    var taskLoadedId by remember { mutableLongStateOf(-1L) }
    val lineEdits = remember { mutableStateListOf<PutawayLineEdit>() }
    // Lines as last loaded from the server; a line differing from its baseline has unsaved edits.
    val putawayBaseline = remember { mutableMapOf<Long, PutawayLineEdit>() }
    var lineHighlights by remember { mutableStateOf<Map<String, ScanMatchStatus>>(emptyMap()) }
    var rfidLocation by remember { mutableStateOf<PickerOption?>(null) }
    var locationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var pickerOpenForLine by remember { mutableIntStateOf(-1) }
    var rfidPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(step, lineEdits.size, lineEdits.map { "${it.itemId}:${it.qtyPutaway}:${it.locationId}" }) {
        val id = (step as? PutawayStep.Detail)?.id ?: return@LaunchedEffect
        delay(500)
        container.workflowDraftStore.save("putaway_lines_$id", Gson().toJson(lineEdits.map { it.copy() }))
    }

    val liveList = rememberWorkflowLiveList(enabled = step is PutawayStep.List) { page ->
        val whId = container.workspaceContext().warehouseId
        val res = container.api.listPutawayTasks(
            status = "pending,in_progress",
            warehouseId = whId,
            page = page,
            perPage = 50,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopePage(res, page)
    }
    val listSortSearch = rememberTableSortSearch()
    val emDash = stringResource(R.string.display_empty)
    val colTask = stringResource(R.string.putaway_col_task)
    val colStatus = stringResource(R.string.common_col_status)
    val colGr = stringResource(R.string.putaway_col_gr)
    val colWarehouse = stringResource(R.string.label_warehouse)
    val putawayIndexColumns = listOf(
        IndexColumnSpec(colTask, 1.1f, { it.string("putaway_task_number") ?: "" }) {
            TableCell.Text(it.string("putaway_task_number") ?: emDash, bold = true)
        },
        IndexColumnSpec(colStatus, 0.8f, { it.string("status") ?: "" }) {
            TableCell.Status(it.string("status"))
        },
        IndexColumnSpec(colGr, 0.9f, { it.obj("goods_receipt")?.string("goods_receipt_number") ?: "" }) {
            TableCell.Text(it.obj("goods_receipt")?.string("goods_receipt_number") ?: emDash)
        },
        IndexColumnSpec(colWarehouse, 1f, { it.obj("warehouse")?.string("name") ?: "" }) {
            TableCell.Text(it.obj("warehouse")?.string("name") ?: emDash)
        },
    )

    /**
     * Rebuild the lines from [body]. With [keepLocalEdits] (the 10 s background refresh), a
     * quantity or location the user changed since the last server load is kept instead of being
     * reset to the server value.
     */
    fun applyTask(body: JsonObject?, keepLocalEdits: Boolean = false) {
        val localEdits = if (keepLocalEdits) {
            lineEdits.filter { l ->
                val base = putawayBaseline[l.itemId]
                base == null || base.qtyPutaway != l.qtyPutaway || base.locationId != l.locationId
            }.associateBy { it.itemId }
        } else {
            emptyMap()
        }
        task = body
        locationOptions = body?.array("warehouse_locations")
            ?.mapNotNull { it.asJsonObject }
            ?.mapNotNull(PickerMappers::warehouseLocation)
            ?: emptyList()
        lineEdits.clear()
        body?.let { nestedItems(it, "items") }?.forEach { item ->
            val loc = item.array("locations")?.firstOrNull()?.asJsonObject
            val locId = loc?.long("warehouse_location_id") ?: item.long("suggested_warehouse_location_id")
            val locObj = loc?.obj("warehouse_location") ?: item.obj("suggested_warehouse_location")
            val qty = loc?.double("quantity_putaway") ?: item.double("quantity_putaway") ?: 0.0
            val isRoll = item.isRollStockLine()
            lineEdits.add(
                PutawayLineEdit(
                    itemId = item.long("id") ?: 0L,
                    productId = item.long("product_id") ?: 0L,
                    variationValueId = item.long("variation_value_id"),
                    rollNumber = item.string("roll_number"),
                    productLabel = item.productName(),
                    variationLabel = item.variationLabel(),
                    rollLabel = item.rollDisplayLabel(),
                    isRoll = isRoll,
                    quantityUnitSuffix = item.quantityUnitSuffix(),
                    qtyToPutaway = item.double("quantity_to_putaway") ?: 0.0,
                    locationId = locId,
                    locationLabel = WorkflowJson.locationLabel(locObj).ifBlank { locId?.toString() ?: "" },
                    qtyPutaway = DisplayFormat.qty(qty),
                    existingLocationRowId = loc?.long("id"),
                ),
            )
        }
        putawayBaseline.clear()
        lineEdits.forEach { putawayBaseline[it.itemId] = it.copy() }
        lineEdits.forEachIndexed { i, l ->
            localEdits[l.itemId]?.let { edit ->
                lineEdits[i] = l.copy(
                    qtyPutaway = edit.qtyPutaway,
                    locationId = edit.locationId,
                    locationLabel = edit.locationLabel,
                )
            }
        }
    }

    fun mergePutawayDraft(taskId: Long) {
        container.workflowDraftStore.loadBlocking("putaway_lines_$taskId")?.let { raw ->
            Gson().fromJson(raw, Array<PutawayLineEdit>::class.java)?.forEach { draft ->
                val idx = lineEdits.indexOfFirst { it.itemId == draft.itemId }
                if (idx >= 0) {
                    lineEdits[idx] = lineEdits[idx].copy(
                        locationId = draft.locationId,
                        locationLabel = draft.locationLabel,
                        qtyPutaway = draft.qtyPutaway,
                    )
                }
            }
        }
    }

    fun beginTaskDetail(id: Long) {
        message = null
        task = null
        lineEdits.clear()
        taskLoading = true
        taskLoadedId = -1L
        step = PutawayStep.Detail(id)
    }

    fun openTask(id: Long) {
        beginTaskDetail(id)
        scope.launchWorkflow(
            setLoading = { },
            onError = { message = it; taskLoading = false },
        ) {
            var res = container.api.getPutawayTask(id)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            var body = WorkflowJson.envelopeObject(res)
            if (body?.string("status") == "pending") {
                container.api.startPutawayTask(id)
                res = container.api.getPutawayTask(id)
                body = WorkflowJson.envelopeObject(res)
            }
            applyTask(body ?: error(errEmptyTask))
            taskLoadedId = id
            taskLoading = false
            mergePutawayDraft(id)
            null
        }
    }

    LaunchedEffect(initialTaskId) {
        initialTaskId?.let { openTask(it) }
    }

    LaunchedEffect((step as? PutawayStep.Detail)?.id) {
        val id = (step as? PutawayStep.Detail)?.id ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000)
            if ((step as? PutawayStep.Detail)?.id != id) break
            if (taskLoading || taskLoadedId != id) continue
            runCatching {
                val res = container.api.getPutawayTask(id)
                if (res.isSuccessful) applyTask(WorkflowJson.envelopeObject(res), keepLocalEdits = true)
            }
        }
    }

    ErpScaffold(
        title = when (step) {
            PutawayStep.List -> putawayTitle
            is PutawayStep.Detail -> task?.string("putaway_task_number") ?: putawayTitle
        },
        subtitle = when (step) {
            PutawayStep.List -> putawaySubtitleList
            is PutawayStep.Detail -> UiStrings.apiStatus(task?.string("status"))
        },
        onBack = {
            when (step) {
                PutawayStep.List -> onBack()
                is PutawayStep.Detail -> step = PutawayStep.List
            }
        },
    ) {
        message?.let {
            if (!it.isBenignCancellationMessage()) {
                StatusBanner(it, isError = StatusMessage.looksLikeError(it))
            }
        }

        when (val current = step) {
            PutawayStep.List -> {
                liveList.error?.let { StatusBanner(it, isError = true) }
                LiveSyncIndicator(liveList.lastUpdatedMs)
                JsonIndexListTable(
                    rows = liveList.rows,
                    columns = putawayIndexColumns,
                    sortSearch = listSortSearch,
                    emptyText = stringResource(R.string.putaway_empty_list),
                    searchPlaceholder = stringResource(R.string.putaway_search_placeholder),
                    loading = liveList.loading,
                    loadingMore = liveList.loadingMore,
                    hasMore = liveList.hasMore,
                    totalCount = liveList.totalCount,
                    onLoadMore = liveList.loadMore,
                    onRowClick = { row -> row.long("id")?.let { openTask(it) } },
                    modifier = Modifier.weight(1f),
                )
            }

            is PutawayStep.Detail -> {
                val taskId = current.id
                if (taskLoading || taskLoadedId != taskId) {
                    PutawayDetailSkeleton(Modifier.weight(1f))
                } else {
                val taskStatus = task?.string("status")
                val canEditTask = taskStatus in setOf("pending", "in_progress")
                val taskLockedMsg = stringResource(R.string.putaway_task_locked_message, UiStrings.apiStatus(taskStatus))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!canEditTask) {
                        Text(
                            taskLockedMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val matchLines = lineEdits.map { line ->
                        WorkflowMatchLine(
                            key = line.itemId.toString(),
                            productId = line.productId,
                            variationValueId = line.variationValueId,
                            rollNumber = line.rollNumber,
                            isRoll = line.isRoll,
                            label = buildString {
                                append(line.productLabel)
                                if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                if (line.rollLabel.isNotBlank()) {
                                    append(stringResource(R.string.putaway_line_roll_suffix, line.rollLabel))
                                }
                            },
                        )
                    }
                    WorkflowDataTable(
                        columns = listOf(
                            DataTableColumn(colProduct, 0.95f),
                            DataTableColumn(colVar, 0.5f),
                            DataTableColumn(colRoll, 0.4f),
                            DataTableColumn(colToPut, 0.4f),
                            DataTableColumn(colLocation, 0.65f),
                            DataTableColumn(colPut, 0.4f),
                        ),
                        rowBackground = { index ->
                            lineHighlights[matchLines.getOrNull(index)?.key]
                                ?.let(scanColors::forStatus)
                                ?: androidx.compose.ui.graphics.Color.Transparent
                        },
                        rows = lineEdits.map { line ->
                            listOf(
                                TableCell.Text(line.productLabel),
                                TableCell.Text(line.variationLabel.ifBlank { emDash }),
                                TableCell.Text(line.rollLabel.ifBlank { if (line.isRoll) emDash else "" }),
                                TableCell.Text(
                                    formatQtyWithUnit(
                                        DisplayFormat.qty(line.qtyToPutaway),
                                        line.quantityUnitSuffix,
                                        line.isRoll,
                                    ),
                                ),
                                TableCell.Text(line.locationLabel.ifBlank { emDash }),
                                TableCell.Text(
                                    formatQtyWithUnit(line.qtyPutaway, line.quantityUnitSuffix, line.isRoll),
                                ),
                            )
                        },
                        emptyText = stringResource(R.string.putaway_empty_items),
                    )
                    if (canEditTask) {
                        lineEdits.forEachIndexed { index, line ->
                            val putLabel = if (line.isRoll) {
                                WorkflowJson.rollLengthLabel(line.quantityUnitSuffix, labelPutawayLength)
                            } else {
                                labelQtyPutaway
                            }
                            Text(
                                buildString {
                                    append(line.productLabel)
                                    if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                    if (line.rollLabel.isNotBlank()) {
                                        append(stringResource(R.string.putaway_line_roll_suffix, line.rollLabel))
                                    }
                                    if (line.isRoll && line.rollLabel.isBlank()) append(" ").append(rollMarker)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                            SearchablePickerField(
                                label = stringResource(R.string.putaway_label_storage_location),
                                selected = line.locationId?.let {
                                    PickerOption(
                                        it,
                                        line.locationLabel.ifBlank {
                                            stringResource(R.string.putaway_fallback_location, it)
                                        },
                                    )
                                },
                                placeholder = stringResource(R.string.putaway_placeholder_location),
                                onOpen = { pickerOpenForLine = index },
                            )
                            QtyField(
                                value = line.qtyPutaway,
                                onValueChange = { v -> lineEdits[index] = line.copy(qtyPutaway = v) },
                                label = buildString {
                                    append(putLabel)
                                    line.quantityUnitSuffix?.let { append(" (").append(it).append(")") }
                                },
                                fillValue = line.qtyToPutaway,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        SearchablePickerField(
                            label = stringResource(R.string.putaway_label_rfid_location),
                            selected = rfidLocation,
                            placeholder = stringResource(R.string.putaway_placeholder_rfid_location),
                            onOpen = { rfidPickerOpen = true },
                            onClear = { rfidLocation = null },
                        )
                    }
                    WorkflowLineScanSection(
                        container = container,
                        tags = tags,
                        lines = matchLines,
                        onIncrementLine = if (canEditTask) {
                            { key, entry ->
                                val index = lineEdits.indexOfFirst { it.itemId.toString() == key }
                                if (index < 0) return@WorkflowLineScanSection
                                val line = lineEdits[index]
                                val delta = scanIncrementDelta(entry)
                                lineEdits[index] = line.copy(
                                    qtyPutaway = incrementQtyField(line.qtyPutaway, delta),
                                )
                            }
                        } else {
                            null
                        },
                        onLineHighlightsChanged = { lineHighlights = it },
                        onClear = { container.rfidManager.clearScannedTags() },
                    )
                    if (canEditTask) {
                    ErpPrimaryButton(text = stringResource(R.string.putaway_btn_rfid_confirm), onClick = {
                        scope.launchWorkflow(
                            onError = { message = it },
                            onSuccess = { message = it },
                        ) {
                            val epcs = tags.map { it.epc }
                            if (epcs.isEmpty()) error(errScanTagsFirst)
                            val res = container.api.putawayRfidConfirm(
                                taskId,
                                PutawayConfirmRequest(epcs, rfidLocation?.id, UUID.randomUUID().toString()),
                            )
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            queueReads(container, "putaway", epcs)
                            res.body()?.message ?: msgConfirmed
                        }
                    })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ErpPrimaryButton(
                            text = stringResource(R.string.putaway_btn_save),
                            modifier = Modifier.weight(1f),
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val items = lineEdits.map { line ->
                                        PutawayItemUpdate(
                                            id = line.itemId,
                                            locations = listOf(
                                                PutawayLocationRow(
                                                    id = line.existingLocationRowId,
                                                    warehouse_location_id = line.locationId,
                                                    quantity_putaway = line.qtyPutaway.toDoubleOrNull(),
                                                ),
                                            ),
                                        )
                                    }
                                    val body = UpdatePutawayRequest(items)
                                    val res = container.workflowApi.executeOrQueue(
                                        endpoint = "putaway-tasks/$taskId",
                                        method = "PUT",
                                        body = body,
                                    ) { container.api.updatePutawayTask(taskId, body) }
                                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                                    applyTask(WorkflowJson.envelopeObject(res))
                                    container.workflowDraftStore.clear("putaway_lines_$taskId")
                                    msgPutawaySaved
                                }
                            },
                        )
                        ErpPrimaryButton(
                            text = stringResource(R.string.putaway_btn_complete),
                            modifier = Modifier.weight(1f),
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val items = lineEdits.map { line ->
                                        PutawayItemUpdate(
                                            id = line.itemId,
                                            locations = listOf(
                                                PutawayLocationRow(
                                                    id = line.existingLocationRowId,
                                                    warehouse_location_id = line.locationId,
                                                    quantity_putaway = line.qtyPutaway.toDoubleOrNull(),
                                                ),
                                            ),
                                        )
                                    }
                                    val body = UpdatePutawayRequest(items, complete_task = "1")
                                    val res = container.workflowApi.executeOrQueue(
                                        endpoint = "putaway-tasks/$taskId",
                                        method = "PUT",
                                        body = body,
                                    ) { container.api.updatePutawayTask(taskId, body) }
                                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                                    container.workflowDraftStore.clear("putaway_lines_$taskId")
                                    step = PutawayStep.List
                                    liveList.refresh()
                                    msgPutawayCompleted
                                }
                            },
                        )
                    }
                    }
                }
                }
            }
        }
    }

    SearchablePickerSheet(
        visible = pickerOpenForLine >= 0,
        title = stringResource(R.string.putaway_label_storage_location),
        options = locationOptions,
        onDismiss = { pickerOpenForLine = -1 },
        onSelect = { option ->
            val idx = pickerOpenForLine
            if (idx >= 0 && idx < lineEdits.size) {
                val line = lineEdits[idx]
                lineEdits[idx] = line.copy(locationId = option.id, locationLabel = option.title)
            }
        },
        searchHint = stringResource(R.string.putaway_picker_search_location),
    )
    SearchablePickerSheet(
        visible = rfidPickerOpen,
        title = stringResource(R.string.putaway_label_rfid_location),
        options = locationOptions,
        onDismiss = { rfidPickerOpen = false },
        onSelect = { rfidLocation = it },
        searchHint = stringResource(R.string.putaway_picker_search_rfid_location),
    )
}
