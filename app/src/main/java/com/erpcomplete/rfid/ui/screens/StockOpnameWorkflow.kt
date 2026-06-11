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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.CreateStockOpnameRequest
import com.erpcomplete.rfid.data.remote.SaveStockOpnameCheckRequest
import com.erpcomplete.rfid.data.remote.StockOpnameCheckItem
import com.erpcomplete.rfid.data.remote.WorkflowScanRequest
import com.erpcomplete.rfid.ui.components.DataTableColumn
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.IndexColumnSpec
import com.erpcomplete.rfid.ui.components.JsonIndexListTable
import com.erpcomplete.rfid.ui.components.StockOpnameDetailSkeleton
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.TableCell
import com.erpcomplete.rfid.ui.components.ScanMatchStatus
import com.erpcomplete.rfid.ui.components.WorkflowDataTable
import com.erpcomplete.rfid.ui.components.WorkflowLineScanSection
import com.erpcomplete.rfid.ui.components.WorkflowMatchLine
import com.erpcomplete.rfid.ui.components.incrementQtyField
import com.erpcomplete.rfid.ui.components.rememberWorkflowLiveList
import com.erpcomplete.rfid.ui.components.rememberTableSortSearch
import com.erpcomplete.rfid.ui.components.scanIncrementDelta
import com.erpcomplete.rfid.ui.components.rememberScanMatchColors
import androidx.compose.runtime.collectAsState
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.nestedItems
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.productSku
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.workspaceContext
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.time.LocalDate
import kotlinx.coroutines.delay

private sealed class OpnameStep {
    data object List : OpnameStep()
    data object Create : OpnameStep()
    data class Count(val id: Long) : OpnameStep()
}

private fun OpnameStep.encode(): String = when (this) {
    OpnameStep.List -> "list"
    OpnameStep.Create -> "create"
    is OpnameStep.Count -> "count:$id"
}

private fun decodeOpnameStep(raw: String): OpnameStep {
    val parts = raw.split(':', limit = 2)
    return when (parts[0]) {
        "create" -> OpnameStep.Create
        "count" -> OpnameStep.Count(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        else -> OpnameStep.List
    }
}

private val OpnameStepSaver = androidx.compose.runtime.saveable.Saver<OpnameStep, String>(
    save = { it.encode() },
    restore = { decodeOpnameStep(it) },
)

private const val OPNAME_LIST_STATUSES = "draft,in_progress,completed"

private data class OpnameLineEdit(
    val itemId: Long?,
    val productId: Long,
    val productLabel: String,
    val productSku: String,
    val variationLabel: String,
    val locationLabel: String,
    val systemQty: Double,
    var countedQty: String,
    val variationValueId: Long?,
    val warehouseLocationId: Long?,
    val rollNumber: String?,
    val rollLength: Double?,
    val quantityUnitSuffix: String?,
    val isRoll: Boolean,
)

@Composable
fun StockOpnameScreen(container: AppContainer, onBack: () -> Unit) {
    var step by rememberSaveable(stateSaver = OpnameStepSaver) { mutableStateOf(OpnameStep.List) }
    var message by remember { mutableStateOf<String?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    val tags by container.rfidManager.scannedTags.collectAsState()
    val scope = rememberCoroutineScope()
    val scanColors = rememberScanMatchColors()
    var lineHighlights by remember { mutableStateOf<Map<String, ScanMatchStatus>>(emptyMap()) }

    var opname by remember { mutableStateOf<JsonObject?>(null) }
    var countLoading by remember { mutableStateOf(false) }
    var countLoadedId by remember { mutableLongStateOf(-1L) }
    val lineEdits = remember { mutableStateListOf<OpnameLineEdit>() }
    var createType by remember { mutableStateOf("cycle_count") }
    var createNotes by remember { mutableStateOf("") }
    val workspaceWhId = container.workspaceContext().warehouseId

    LaunchedEffect(step, lineEdits.size, lineEdits.map { it.countedQty }) {
        val id = (step as? OpnameStep.Count)?.id ?: return@LaunchedEffect
        delay(500)
        val payload = Gson().toJson(lineEdits.map { it.copy() })
        container.workflowDraftStore.save("opname_lines_$id", payload)
    }

    val liveList = rememberWorkflowLiveList(enabled = step is OpnameStep.List) { page ->
        val whId = container.workspaceContext().warehouseId
        val res = container.api.listStockOpnames(
            status = OPNAME_LIST_STATUSES,
            warehouseId = whId,
            page = page,
            perPage = 50,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopePage(res, page)
    }
    val listSortSearch = rememberTableSortSearch()
    val opnameIndexColumns = remember {
        listOf(
            IndexColumnSpec("Opname #", 1.1f, { it.string("opname_number") ?: "" }) {
                TableCell.Text(it.string("opname_number") ?: "—", bold = true)
            },
            IndexColumnSpec("Status", 0.85f, { it.string("status") ?: "" }) {
                TableCell.Status(it.string("status"))
            },
            IndexColumnSpec("Warehouse", 1f, { it.obj("warehouse")?.string("name") ?: "" }) {
                TableCell.Text(it.obj("warehouse")?.string("name") ?: "—")
            },
            IndexColumnSpec("Type", 0.75f, { it.string("opname_type") ?: "" }) {
                TableCell.Text(DisplayFormat.status(it.string("opname_type") ?: ""))
            },
        )
    }

    fun applyOpname(body: JsonObject?) {
        opname = body
        lineEdits.clear()
        body?.let { nestedItems(it) }?.forEach { item ->
            val counted = item.double("counted_quantity")
            val isRoll = item.isRollStockLine()
            lineEdits.add(
                OpnameLineEdit(
                    itemId = item.long("id"),
                    productId = item.long("product_id") ?: 0L,
                    productLabel = item.productName(),
                    productSku = item.productSku(),
                    variationLabel = item.variationLabel(),
                    locationLabel = WorkflowJson.locationLabel(item.obj("warehouse_location")).ifBlank { "—" },
                    systemQty = item.double("system_quantity") ?: 0.0,
                    countedQty = if (counted != null) DisplayFormat.qty(counted) else "",
                    variationValueId = item.long("variation_value_id"),
                    warehouseLocationId = item.long("warehouse_location_id"),
                    rollNumber = item.string("roll_number"),
                    rollLength = item.double("roll_length"),
                    quantityUnitSuffix = item.quantityUnitSuffix(),
                    isRoll = isRoll,
                ),
            )
        }
    }

    fun mergeOpnameDraft(opnameId: Long) {
        container.workflowDraftStore.loadBlocking("opname_lines_$opnameId")?.let { raw ->
            Gson().fromJson(raw, Array<OpnameLineEdit>::class.java)?.forEach { draft ->
                val idx = lineEdits.indexOfFirst { it.itemId == draft.itemId }
                if (idx >= 0) lineEdits[idx] = lineEdits[idx].copy(countedQty = draft.countedQty)
            }
        }
    }

    fun beginCount(id: Long) {
        message = null
        opname = null
        lineEdits.clear()
        countLoading = true
        countLoadedId = -1L
        step = OpnameStep.Count(id)
    }

    fun openCount(id: Long) {
        beginCount(id)
        scope.launchWorkflow(
            setLoading = { },
            onError = { message = it; countLoading = false },
        ) {
            val res = container.api.startStockOpnameCheck(id)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            applyOpname(WorkflowJson.envelopeObject(res) ?: error("Empty opname"))
            countLoadedId = id
            countLoading = false
            mergeOpnameDraft(id)
            null
        }
    }

    val title = when (val current = step) {
        OpnameStep.List -> "Stock opname"
        OpnameStep.Create -> "New stock opname"
        is OpnameStep.Count -> opname?.string("opname_number") ?: "Stock count"
    }

    ErpScaffold(
        title = title,
        subtitle = when (val current = step) {
            OpnameStep.List -> "Draft & ongoing counts"
            OpnameStep.Create -> "Schedule a count for your warehouse"
            is OpnameStep.Count -> DisplayFormat.status(opname?.string("status"))
        },
        onBack = {
            when (step) {
                OpnameStep.List -> onBack()
                OpnameStep.Create -> step = OpnameStep.List
                is OpnameStep.Count -> step = OpnameStep.List
            }
        },
    ) {
        message?.let {
            StatusBanner(it, isError = it.contains("Error", true) || it.contains("Failed", true))
        }

        when (val current = step) {
            OpnameStep.List -> {
                liveList.error?.let { StatusBanner(it, isError = true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LiveSyncIndicator(liveList.lastUpdatedMs)
                    ErpPrimaryButton(
                        text = "New opname",
                        onClick = {
                            message = null
                            createType = "cycle_count"
                            createNotes = ""
                            step = OpnameStep.Create
                        },
                        modifier = Modifier.fillMaxWidth(0.48f),
                    )
                }
                JsonIndexListTable(
                    rows = liveList.rows,
                    columns = opnameIndexColumns,
                    sortSearch = listSortSearch,
                    emptyText = "No stock opnames yet. Tap New opname to start.",
                    searchPlaceholder = "Search opnames…",
                    loading = liveList.loading,
                    loadingMore = liveList.loadingMore,
                    hasMore = liveList.hasMore,
                    totalCount = liveList.totalCount,
                    onLoadMore = liveList.loadMore,
                    onRowClick = { row -> row.long("id")?.let { openCount(it) } },
                    modifier = Modifier.weight(1f),
                )
            }

            OpnameStep.Create -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Warehouse: ${container.authStore.warehouseNameBlocking() ?: workspaceWhId?.toString() ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        createType,
                        { createType = it },
                        label = { Text("Type (full, partial, cycle_count)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        createNotes,
                        { createNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ErpPrimaryButton(
                        text = "Create & start counting",
                        loading = actionLoading,
                        onClick = {
                            val whId = workspaceWhId ?: return@ErpPrimaryButton
                            scope.launchWorkflow(
                                setLoading = { actionLoading = it },
                                onError = { message = it },
                            ) {
                                val body = CreateStockOpnameRequest(
                                    warehouse_id = whId,
                                    opname_date = LocalDate.now().toString(),
                                    opname_type = createType.ifBlank { "cycle_count" },
                                    notes = createNotes.ifBlank { null },
                                )
                                val res = container.api.createStockOpname(body)
                                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                                val created = WorkflowJson.envelopeObject(res)
                                    ?: error("Empty opname response")
                                val id = created.long("id") ?: error("Missing opname id")
                                val check = container.api.startStockOpnameCheck(id)
                                if (!check.isSuccessful) error(ApiErrorParser.httpMessage(check, authenticated = true))
                                applyOpname(WorkflowJson.envelopeObject(check) ?: error("Empty check"))
                                countLoadedId = id
                                countLoading = false
                                step = OpnameStep.Count(id)
                                liveList.refresh()
                                null
                            }
                        },
                    )
                }
            }

            is OpnameStep.Count -> {
                val opnameId = current.id
                if (countLoading || countLoadedId != opnameId) {
                    StockOpnameDetailSkeleton(Modifier.weight(1f))
                } else {
                val status = opname?.string("status")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Warehouse: ${opname?.obj("warehouse")?.string("name") ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val matchLines = lineEdits.map { line ->
                        WorkflowMatchLine(
                            key = line.itemId?.toString() ?: "${line.productId}_${line.variationValueId}_${line.rollNumber}",
                            productId = line.productId,
                            variationValueId = line.variationValueId,
                            rollNumber = line.rollNumber,
                            isRoll = line.isRoll,
                            label = buildString {
                                append(line.productLabel)
                                if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                line.rollNumber?.let { append(" · roll ").append(it) }
                            },
                        )
                    }
                    WorkflowDataTable(
                        columns = listOf(
                            DataTableColumn("Product", 0.95f),
                            DataTableColumn("Var", 0.6f),
                            DataTableColumn("Loc", 0.45f),
                            DataTableColumn("Roll", 0.4f),
                            DataTableColumn("Sys", 0.35f),
                            DataTableColumn("Cnt", 0.35f),
                            DataTableColumn("Δ", 0.3f),
                        ),
                        rowBackground = { index ->
                            lineHighlights[matchLines.getOrNull(index)?.key]
                                ?.let(scanColors::forStatus)
                                ?: androidx.compose.ui.graphics.Color.Transparent
                        },
                        rows = lineEdits.map { line ->
                            val counted = line.countedQty.toDoubleOrNull()
                            val variance = if (counted != null) counted - line.systemQty else null
                            val rollLabel = when {
                                line.rollNumber != null -> line.rollNumber
                                line.isRoll && line.rollLength != null -> DisplayFormat.qty(line.rollLength)
                                else -> "—"
                            }
                            listOf(
                                TableCell.Text(
                                    buildString {
                                        append(line.productLabel)
                                        if (line.productSku.isNotBlank()) append("\n").append(line.productSku)
                                    },
                                ),
                                TableCell.Text(line.variationLabel.ifBlank { "—" }),
                                TableCell.Text(line.locationLabel.ifBlank { "—" }),
                                TableCell.Text(rollLabel),
                                TableCell.Text(
                                    formatQtyWithUnit(
                                        DisplayFormat.qty(line.systemQty),
                                        line.quantityUnitSuffix,
                                        line.isRoll,
                                    ),
                                ),
                                TableCell.Text(
                                    if (line.countedQty.isBlank()) "—"
                                    else formatQtyWithUnit(line.countedQty, line.quantityUnitSuffix, line.isRoll),
                                ),
                                TableCell.Text(
                                    variance?.let {
                                        formatQtyWithUnit(DisplayFormat.qty(it), line.quantityUnitSuffix, line.isRoll)
                                    } ?: "—",
                                ),
                            )
                        },
                        emptyText = "No stock lines in snapshot.",
                    )
                    if (status != "approved") {
                        WorkflowLineScanSection(
                            container = container,
                            tags = tags,
                            lines = matchLines,
                            onIncrementLine = { key, entry ->
                                val index = lineEdits.indexOfFirst {
                                    (it.itemId?.toString() ?: "${it.productId}_${it.variationValueId}_${it.rollNumber}") == key
                                }
                                if (index < 0) return@WorkflowLineScanSection
                                val line = lineEdits[index]
                                val delta = scanIncrementDelta(entry)
                                lineEdits[index] = line.copy(
                                    countedQty = incrementQtyField(line.countedQty.ifBlank { "0" }, delta),
                                )
                            },
                            onLineHighlightsChanged = { lineHighlights = it },
                            onClear = { container.rfidManager.clearScannedTags() },
                        )
                        lineEdits.forEachIndexed { index, line ->
                            val countLabel = if (line.isRoll) {
                                WorkflowJson.rollLengthLabel(line.quantityUnitSuffix, "Counted length")
                            } else {
                                "Counted qty"
                            }
                            OutlinedTextField(
                                line.countedQty,
                                { v -> lineEdits[index] = line.copy(countedQty = v) },
                                label = {
                                    Text(
                                        "$countLabel — ${line.productLabel}" +
                                            line.variationLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
                                            line.rollNumber?.let { " · roll $it" }.orEmpty(),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (status != "approved") {
                        ErpPrimaryButton(
                            text = "Save counts",
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val items = lineEdits.map { line ->
                                        StockOpnameCheckItem(
                                            item_id = line.itemId,
                                            product_id = line.productId,
                                            variation_value_id = line.variationValueId,
                                            warehouse_location_id = line.warehouseLocationId,
                                            system_quantity = line.systemQty,
                                            counted_quantity = line.countedQty.toDoubleOrNull(),
                                            roll_number = line.rollNumber,
                                            roll_length = line.rollLength ?: if (line.isRoll) line.systemQty else null,
                                        )
                                    }
                                    val body = SaveStockOpnameCheckRequest(items)
                                    val res = container.workflowApi.executeOrQueue(
                                        endpoint = "stock-opnames/stock-check/$opnameId",
                                        method = "POST",
                                        body = body,
                                    ) {
                                        container.api.saveStockOpnameCheck(opnameId, body)
                                    }
                                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                                    val epcs = tags.map { it.epc }.distinct()
                                    if (epcs.isNotEmpty()) {
                                        val rfidRes = container.api.stockOpnameRfidCountConfirm(
                                            opnameId,
                                            WorkflowScanRequest(epcs),
                                        )
                                        if (!rfidRes.isSuccessful) {
                                            error(ApiErrorParser.httpMessage(rfidRes, authenticated = true))
                                        }
                                    }
                                    applyOpname(WorkflowJson.envelopeObject(res))
                                    container.workflowDraftStore.clear("opname_lines_$opnameId")
                                    "Counts saved"
                                }
                            },
                        )
                    }
                    if (status == "in_progress") {
                        ErpPrimaryButton(
                            text = "Done counting",
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val res = container.api.doneStockOpnameCounting(opnameId)
                                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                                    applyOpname(WorkflowJson.envelopeObject(res))
                                    liveList.refresh()
                                    "Marked complete — ready to approve"
                                }
                            },
                        )
                    }
                    if (status == "completed") {
                        ErpPrimaryButton(
                            text = "Approve & adjust stock",
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val res = container.api.approveStockOpname(opnameId)
                                    val root = res.body()?.asJsonObject
                                    if (!res.isSuccessful || root?.get("success")?.asBoolean == false) {
                                        error(root?.get("message")?.asString ?: ApiErrorParser.httpMessage(res))
                                    }
                                    applyOpname(WorkflowJson.envelopeObject(container.api.getStockOpname(opnameId)))
                                    liveList.refresh()
                                    root?.get("message")?.asString ?: "Stock opname approved"
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
