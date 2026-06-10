package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.UpdatePackCutRequest
import com.erpcomplete.rfid.data.remote.UpdatePickListRequest
import com.erpcomplete.rfid.data.remote.WorkflowScanRequest
import com.erpcomplete.rfid.ui.components.DataTableColumn
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.IndexColumnSpec
import com.erpcomplete.rfid.ui.components.JsonIndexListTable
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
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
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.array
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.nestedItems
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.productSku
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.unwrapPickList
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.erpcomplete.rfid.util.isBenignCancellationMessage
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.workspaceContext
import com.google.gson.JsonObject
import java.util.UUID

private sealed class PickStep {
    data object List : PickStep()
    data class Detail(val id: Long) : PickStep()
}

private fun PickStep.encode(): String = when (this) {
    PickStep.List -> "list"
    is PickStep.Detail -> "detail:$id"
}

private fun decodePickStep(raw: String): PickStep {
    val parts = raw.split(':', limit = 2)
    return when (parts[0]) {
        "detail" -> PickStep.Detail(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        else -> PickStep.List
    }
}

private val PickStepSaver = androidx.compose.runtime.saveable.Saver<PickStep, String>(
    save = { it.encode() },
    restore = { decodePickStep(it) },
)

private data class PickLineEdit(
    val itemId: Long,
    val productId: Long,
    val variationValueId: Long?,
    val productLabel: String,
    val variationLabel: String,
    val rollNumber: String?,
    val rollLength: Double?,
    val quantityUnitSuffix: String?,
    val requested: Double,
    var picked: String,
    val isRoll: Boolean,
    val hasContainerAssignments: Boolean,
    var packed: String,
    var cutLengths: String,
)

private data class ContainerPackEdit(
    val containerItemId: Long,
    val label: String,
    val picked: Double,
    var packed: String,
)

private fun JsonObject.boolean(key: String): Boolean? =
    if (!has(key) || get(key).isJsonNull) null else get(key).asBoolean

private fun parseCutLengthsFromNotes(notes: String?): String {
    if (notes.isNullOrBlank() || !notes.contains("Cut lengths:")) return ""
    val segment = Regex("Cut lengths:\\s*([^|]+)").find(notes)?.groupValues?.get(1) ?: return ""
    return segment.split(",")
        .map { it.replace(Regex("[^0-9.\\-]"), "").trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")
}

private fun cutLengthsToJson(cutLengths: String): String {
    val values = cutLengths.split(",")
        .map { it.trim() }
        .mapNotNull { it.toDoubleOrNull() }
        .filter { it > 0.0 }
    return values.joinToString(prefix = "[", postfix = "]", separator = ",")
}

private fun rollCutGroupKey(line: PickLineEdit): String {
    val rollToken = line.rollNumber?.takeIf { it.isNotBlank() } ?: "item_${line.itemId}"
    return "${line.productId}_$rollToken"
}

private fun itemHasContainerAssignments(pickList: JsonObject, itemId: Long): Boolean {
    pickList.array("containers")?.forEach { containerEl ->
        containerEl.asJsonObject.array("container_items")?.forEach { ciEl ->
            if (ciEl.asJsonObject.long("pick_list_item_id") == itemId) return true
        }
    }
    return false
}

private fun qtyWithUnit(qty: String, unit: String?, isRoll: Boolean): String =
    WorkflowJson.formatQtyWithUnit(qty, unit, isRoll)

@Composable
fun PickScreen(container: AppContainer, onBack: () -> Unit) {
    var step by rememberSaveable(stateSaver = PickStepSaver) { mutableStateOf(PickStep.List) }
    var message by remember { mutableStateOf<String?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    val tags by container.rfidManager.scannedTags.collectAsState()
    val scope = rememberCoroutineScope()
    val scanColors = rememberScanMatchColors()

    var pickList by remember { mutableStateOf<JsonObject?>(null) }
    val lineEdits = remember { mutableStateListOf<PickLineEdit>() }
    var lineHighlights by remember { mutableStateOf<Map<String, ScanMatchStatus>>(emptyMap()) }
    val containerEdits = remember { mutableStateListOf<ContainerPackEdit>() }
    var detailTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(step, lineEdits.size, lineEdits.map { "${it.itemId}:${it.picked}:${it.packed}" }) {
        val id = (step as? PickStep.Detail)?.id ?: return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        com.google.gson.Gson().toJson(lineEdits.map { it.copy() }).let {
            container.workflowDraftStore.save("pick_lines_$id", it)
        }
    }

    val liveList = rememberWorkflowLiveList(enabled = step is PickStep.List) { page ->
        val whId = container.workspaceContext().warehouseId
        val res = container.api.listPickLists(
            pickStatus = "pending,in_progress",
            warehouseId = whId,
            page = page,
            perPage = 50,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopePage(res, page)
    }
    val listSortSearch = rememberTableSortSearch()
    val pickIndexColumns = remember {
        listOf(
            IndexColumnSpec("Pick #", 1.1f, { it.string("pick_list_number") ?: "" }) {
                TableCell.Text(it.string("pick_list_number") ?: "—", bold = true)
            },
            IndexColumnSpec("Status", 0.85f, { it.string("pick_status") ?: "" }) {
                TableCell.Status(it.string("pick_status"))
            },
            IndexColumnSpec("Warehouse", 1f, { it.obj("warehouse")?.string("name") ?: "" }) {
                TableCell.Text(it.obj("warehouse")?.string("name") ?: "—")
            },
            IndexColumnSpec("Date", 0.75f, { it.string("pick_date") ?: "" }) {
                TableCell.Date(it.string("pick_date"))
            },
        )
    }

    fun applyPickList(body: JsonObject?) {
        val resolved = unwrapPickList(body)
        pickList = resolved
        lineEdits.clear()
        containerEdits.clear()
        resolved?.let { pl ->
            nestedItems(pl, "items").forEach { item ->
                val roll = item.string("roll_number")
                val isRoll = item.isRollStockLine() || item.boolean("is_roll_quantity") == true
                val unit = item.string("quantity_unit_suffix")
                val notes = item.string("notes")
                val itemId = item.long("id") ?: 0L
                lineEdits.add(
                    PickLineEdit(
                        itemId = itemId,
                        productId = item.long("product_id") ?: 0L,
                        variationValueId = item.long("variation_value_id"),
                        productLabel = item.productName(),
                        variationLabel = item.variationLabel(),
                        rollNumber = roll,
                        rollLength = item.double("roll_length"),
                        quantityUnitSuffix = unit,
                        requested = item.double("requested_quantity") ?: 0.0,
                        picked = DisplayFormat.qty(item.double("picked_quantity") ?: 0.0),
                        isRoll = isRoll,
                        hasContainerAssignments = itemHasContainerAssignments(pl, itemId),
                        packed = DisplayFormat.qty(item.double("packed_quantity") ?: 0.0),
                        cutLengths = parseCutLengthsFromNotes(notes).ifBlank {
                            item.double("cut_quantity")?.takeIf { it > 0.0 }?.let { DisplayFormat.qty(it) }.orEmpty()
                        },
                    ),
                )
            }
            pl.array("containers")?.forEach { containerEl ->
                val container = containerEl.asJsonObject
                val containerLabel = container.string("container_name")
                    ?: container.string("container_number")
                    ?: "Container"
                container.array("container_items")?.forEach { ciEl ->
                    val ci = ciEl.asJsonObject
                    val product = ci.obj("product")?.string("name") ?: "Product"
                    val variation = ci.variationLabel().ifBlank { ci.obj("pick_list_item")?.variationLabel().orEmpty() }
                    val sku = ci.productSku()
                    containerEdits.add(
                        ContainerPackEdit(
                            containerItemId = ci.long("id") ?: 0L,
                            label = buildString {
                                append(containerLabel)
                                append(" · ")
                                append(product)
                                if (variation.isNotBlank()) append(" · ").append(variation)
                                if (sku.isNotBlank()) append(" (").append(sku).append(")")
                            },
                            picked = ci.double("picked_quantity") ?: 0.0,
                            packed = DisplayFormat.qty(ci.double("packed_quantity") ?: 0.0),
                        ),
                    )
                }
            }
        }
        val allPicked = lineEdits.all { (it.picked.toDoubleOrNull() ?: 0.0) >= it.requested }
        if (allPicked && lineEdits.isNotEmpty()) detailTab = 1
    }

    fun mergePickDraft(pickId: Long) {
        container.workflowDraftStore.loadBlocking("pick_lines_$pickId")?.let { raw ->
            com.google.gson.Gson().fromJson(raw, Array<PickLineEdit>::class.java)?.forEach { draft ->
                val idx = lineEdits.indexOfFirst { it.itemId == draft.itemId }
                if (idx >= 0) {
                    lineEdits[idx] = lineEdits[idx].copy(
                        picked = draft.picked,
                        packed = draft.packed,
                        cutLengths = draft.cutLengths,
                    )
                }
            }
        }
    }

    fun openPick(id: Long) {
        message = null
        scope.launchWorkflow(
            setLoading = { actionLoading = it },
            onError = { message = it },
        ) {
            val res = container.api.getPickList(id)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            applyPickList(WorkflowJson.envelopeObject(res) ?: error("Empty pick list"))
            step = PickStep.Detail(id)
            mergePickDraft(id)
            null
        }
    }

    LaunchedEffect((step as? PickStep.Detail)?.id) {
        val id = (step as? PickStep.Detail)?.id ?: return@LaunchedEffect
        while (true) {
            runCatching {
                val res = container.api.getPickList(id)
                if (res.isSuccessful) applyPickList(WorkflowJson.envelopeObject(res))
            }
            kotlinx.coroutines.delay(10_000)
        }
    }

    ErpScaffold(
        title = when (step) {
            PickStep.List -> "Pick list"
            is PickStep.Detail -> pickList?.string("pick_list_number") ?: "Pick list"
        },
        subtitle = when (step) {
            PickStep.List -> "Tap a row · auto-syncs"
            is PickStep.Detail -> DisplayFormat.status(pickList?.string("pick_status"))
        },
        onBack = {
            when (step) {
                PickStep.List -> onBack()
                is PickStep.Detail -> step = PickStep.List
            }
        },
    ) {
        message?.let {
            if (!it.isBenignCancellationMessage()) {
                StatusBanner(it, isError = it.contains("Error", true))
            }
        }

        when (val current = step) {
            PickStep.List -> {
                liveList.error?.let { StatusBanner(it, isError = true) }
                LiveSyncIndicator(liveList.lastUpdatedMs)
                JsonIndexListTable(
                    rows = liveList.rows,
                    columns = pickIndexColumns,
                    sortSearch = listSortSearch,
                    emptyText = "No pick lists.",
                    searchPlaceholder = "Search pick lists…",
                    loading = liveList.loading,
                    loadingMore = liveList.loadingMore,
                    hasMore = liveList.hasMore,
                    totalCount = liveList.totalCount,
                    onLoadMore = liveList.loadMore,
                    onRowClick = { row -> row.long("id")?.let { openPick(it) } },
                    modifier = Modifier.weight(1f),
                )
            }

            is PickStep.Detail -> {
                val pickId = current.id
                TabRow(detailTab) {
                    Tab(selected = detailTab == 0, onClick = { detailTab = 0 }, text = { Text("Pick") })
                    Tab(selected = detailTab == 1, onClick = { detailTab = 1 }, text = { Text("Pack & cut") })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detailTab == 0) {
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
                                    line.rollNumber?.let { append(" · roll ").append(it) }
                                },
                            )
                        }
                        WorkflowDataTable(
                            columns = listOf(
                                DataTableColumn("Product", 0.95f),
                                DataTableColumn("Var", 0.55f),
                                DataTableColumn("Roll", 0.45f),
                                DataTableColumn("Req", 0.4f),
                                DataTableColumn("Picked", 0.45f),
                                DataTableColumn("Status", 0.65f),
                            ),
                            rowBackground = { index ->
                                lineHighlights[matchLines.getOrNull(index)?.key]
                                    ?.let(scanColors::forStatus)
                                    ?: androidx.compose.ui.graphics.Color.Transparent
                            },
                            rows = lineEdits.map { line ->
                                val picked = line.picked.toDoubleOrNull() ?: 0.0
                                val status = when {
                                    picked <= 0.0 -> "pending"
                                    picked < line.requested -> "partial"
                                    else -> "completed"
                                }
                                val rollLabel = line.rollNumber
                                    ?: line.rollLength?.let { DisplayFormat.qty(it) }
                                    ?: if (line.isRoll) "—" else ""
                                listOf(
                                    TableCell.Text(line.productLabel),
                                    TableCell.Text(line.variationLabel.ifBlank { "—" }),
                                    TableCell.Text(rollLabel.ifBlank { "—" }),
                                    TableCell.Text(qtyWithUnit(DisplayFormat.qty(line.requested), line.quantityUnitSuffix, line.isRoll)),
                                    TableCell.Text(qtyWithUnit(line.picked, line.quantityUnitSuffix, line.isRoll)),
                                    TableCell.Status(status),
                                )
                            },
                            emptyText = "No pick lines.",
                        )
                        lineEdits.forEachIndexed { index, line ->
                            val pickLabel = if (line.isRoll) {
                                WorkflowJson.rollLengthLabel(line.quantityUnitSuffix, "Picked length")
                            } else {
                                "Picked qty"
                            }
                            OutlinedTextField(
                                line.picked,
                                { v -> lineEdits[index] = line.copy(picked = v) },
                                label = {
                                    Text(
                                        buildString {
                                            append(pickLabel)
                                            append(" — ")
                                            append(line.productLabel)
                                            if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                            line.rollNumber?.let { append(" · roll ").append(it) }
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        WorkflowLineScanSection(
                            container = container,
                            tags = tags,
                            lines = matchLines,
                            onIncrementLine = { key, entry ->
                                val index = lineEdits.indexOfFirst { it.itemId.toString() == key }
                                if (index < 0) return@WorkflowLineScanSection
                                val line = lineEdits[index]
                                val delta = scanIncrementDelta(entry)
                                lineEdits[index] = line.copy(
                                    picked = incrementQtyField(line.picked, delta),
                                )
                            },
                            onLineHighlightsChanged = { lineHighlights = it },
                            onClear = { container.rfidManager.clearScannedTags() },
                        )
                        ErpPrimaryButton(text = "Sync picks to server", onClick = {
                            scope.launchWorkflow(
                                onError = { message = it },
                                onSuccess = { message = it },
                            ) {
                                val epcs = tags.map { it.epc }
                                if (epcs.isEmpty()) error("Scan tags first")
                                val res = container.api.pickRfidConfirm(
                                    pickId,
                                    WorkflowScanRequest(epcs, UUID.randomUUID().toString()),
                                )
                                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                                queueReads(container, "pick", epcs)
                                val data = WorkflowJson.envelopeObject(res)
                                data?.array("confirmed")?.forEach { el ->
                                    val row = el.asJsonObject
                                    val itemId = row.long("pick_list_item_id") ?: return@forEach
                                    val scanned = row.double("scanned_tags") ?: row.double("scanned") ?: return@forEach
                                    val idx = lineEdits.indexOfFirst { it.itemId == itemId }
                                    if (idx >= 0) {
                                        lineEdits[idx] = lineEdits[idx].copy(picked = DisplayFormat.qty(scanned))
                                    }
                                }
                                res.body()?.message ?: "Confirmed"
                            }
                        })
                        ErpPrimaryButton(text = "Save picking", loading = actionLoading, onClick = {
                            scope.launchWorkflow(
                                setLoading = { actionLoading = it },
                                onError = { message = it },
                                onSuccess = { message = it },
                            ) {
                                val picked = lineEdits.associate {
                                    it.itemId.toString() to (it.picked.toDoubleOrNull() ?: 0.0)
                                }
                                val body = UpdatePickListRequest(picked_quantities = picked)
                                val res = container.workflowApi.executeOrQueue(
                                    endpoint = "inventory-pick-lists/$pickId",
                                    method = "PUT",
                                    body = body,
                                ) { container.api.updatePickList(pickId, body) }
                                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                                applyPickList(unwrapPickList(WorkflowJson.envelopeObject(res)))
                                container.workflowDraftStore.clear("pick_lines_$pickId")
                                "Picking saved"
                            }
                        })
                        ErpPrimaryButton(text = "Complete picking", loading = actionLoading, onClick = {
                            scope.launchWorkflow(
                                setLoading = { actionLoading = it },
                                onError = { message = it },
                                onSuccess = { message = it },
                            ) {
                                val picked = lineEdits.associate {
                                    it.itemId.toString() to (it.picked.toDoubleOrNull() ?: 0.0)
                                }
                                val body = UpdatePickListRequest(picked_quantities = picked, pick_status = "completed_picking")
                                val res = container.workflowApi.executeOrQueue(
                                    endpoint = "inventory-pick-lists/$pickId",
                                    method = "PUT",
                                    body = body,
                                ) { container.api.updatePickList(pickId, body) }
                                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
                                applyPickList(unwrapPickList(WorkflowJson.envelopeObject(res)))
                                container.workflowDraftStore.clear("pick_lines_$pickId")
                                detailTab = 1
                                "Picking completed"
                            }
                        })
                    } else {
                        if (containerEdits.isNotEmpty()) {
                            Text("Container packing", style = MaterialTheme.typography.labelMedium)
                            WorkflowDataTable(
                                columns = listOf(
                                    DataTableColumn("Assignment", 1.4f),
                                    DataTableColumn("Picked", 0.45f),
                                    DataTableColumn("Packed", 0.45f),
                                ),
                                rows = containerEdits.map { line ->
                                    listOf(
                                        TableCell.Text(line.label),
                                        TableCell.Text(DisplayFormat.qty(line.picked)),
                                        TableCell.Text(line.packed),
                                    )
                                },
                                emptyText = "No container lines.",
                            )
                            containerEdits.forEachIndexed { index, line ->
                                OutlinedTextField(
                                    line.packed,
                                    { v -> containerEdits[index] = line.copy(packed = v) },
                                    label = { Text("Packed — ${line.label}") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        WorkflowDataTable(
                            columns = listOf(
                                DataTableColumn("Product", 0.9f),
                                DataTableColumn("Var", 0.5f),
                                DataTableColumn("Roll", 0.4f),
                                DataTableColumn("Picked", 0.4f),
                                DataTableColumn("Pack/Cut", 0.5f),
                            ),
                            rows = lineEdits.map { line ->
                                val rollLabel = line.rollNumber
                                    ?: line.rollLength?.let { DisplayFormat.qty(it) }
                                    ?: if (line.isRoll) "—" else ""
                                val packCut = when {
                                    line.isRoll -> line.cutLengths.ifBlank { "—" }
                                    line.hasContainerAssignments -> "container"
                                    else -> line.packed
                                }
                                listOf(
                                    TableCell.Text(line.productLabel),
                                    TableCell.Text(line.variationLabel.ifBlank { "—" }),
                                    TableCell.Text(rollLabel.ifBlank { "—" }),
                                    TableCell.Text(qtyWithUnit(line.picked, line.quantityUnitSuffix, line.isRoll)),
                                    TableCell.Text(packCut),
                                )
                            },
                            emptyText = "No lines.",
                        )
                        lineEdits.forEachIndexed { index, line ->
                            when {
                                line.isRoll -> {
                                    OutlinedTextField(
                                        line.cutLengths,
                                        { v -> lineEdits[index] = line.copy(cutLengths = v) },
                                        label = {
                                            Text(
                                                "Cut lengths (${line.quantityUnitSuffix ?: "m"}) — ${line.productLabel}" +
                                                    line.variationLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
                                                    line.rollNumber?.let { " · roll $it" }.orEmpty(),
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                    )
                                    Text(
                                        "Enter one or more lengths separated by commas (e.g. 2.5, 1.0). Total must match requested.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                !line.hasContainerAssignments -> {
                                    OutlinedTextField(
                                        line.packed,
                                        { v -> lineEdits[index] = line.copy(packed = v) },
                                        label = {
                                            Text(
                                                "Packed qty — ${line.productLabel}" +
                                                    line.variationLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        ErpPrimaryButton(text = "Save pack & cut", loading = actionLoading, onClick = {
                            scope.launchWorkflow(
                                setLoading = { actionLoading = it },
                                onError = { message = it },
                                onSuccess = { message = it },
                            ) {
                                val simplePacked = lineEdits
                                    .filter { !it.isRoll && !it.hasContainerAssignments }
                                    .associate { it.itemId.toString() to (it.packed.toDoubleOrNull() ?: 0.0) }
                                val packedQuantities = simplePacked
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { mapOf(pickId.toString() to it) }
                                val cutLengths = lineEdits
                                    .filter { it.isRoll }
                                    .mapNotNull { line ->
                                        val json = cutLengthsToJson(line.cutLengths)
                                        if (json == "[]") null else rollCutGroupKey(line) to json
                                    }
                                    .toMap()
                                    .takeIf { it.isNotEmpty() }
                                val containerPacked = containerEdits
                                    .associate { it.containerItemId.toString() to (it.packed.toDoubleOrNull() ?: 0.0) }
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { mapOf(pickId.toString() to it) }
                                val res = container.api.updatePickListPackCut(
                                    pickId,
                                    UpdatePackCutRequest(
                                        packed_quantities = packedQuantities,
                                        cut_lengths = cutLengths,
                                        packed_container_quantities = containerPacked,
                                    ),
                                )
                                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                                applyPickList(unwrapPickList(WorkflowJson.envelopeObject(res)))
                                "Pack & cut saved"
                            }
                        })
                    }
                }
            }
        }
    }
}
