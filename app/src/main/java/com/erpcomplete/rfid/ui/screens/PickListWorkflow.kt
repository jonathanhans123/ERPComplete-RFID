package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.PickConfirmRequest
import com.erpcomplete.rfid.data.remote.PickScanLookupRequest
import com.erpcomplete.rfid.data.remote.PickScanRequest
import com.erpcomplete.rfid.data.remote.UpdatePackCutRequest
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.data.remote.UpdatePickListRequest
import com.erpcomplete.rfid.data.remote.WorkflowScanRequest
import com.erpcomplete.rfid.ui.components.DataTableColumn
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.QtyField
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.IndexColumnSpec
import com.erpcomplete.rfid.ui.components.JsonIndexListTable
import com.erpcomplete.rfid.ui.components.PickListDetailSkeleton
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
import com.erpcomplete.rfid.util.StatusMessage
import com.erpcomplete.rfid.ui.util.UiStrings
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

/** One bal (container) of the pick list, for the one-tap Pack bal list. */
private data class BalRow(val id: Long, val label: String, val packed: Double, val total: Double)

private fun balRows(pickList: JsonObject?, fallbackLabel: String): List<BalRow> =
    pickList?.array("containers")?.map { el ->
        val c = el.asJsonObject
        val items = c.array("container_items")?.map { it.asJsonObject }.orEmpty()
        BalRow(
            id = c.long("id") ?: 0L,
            label = listOfNotNull(c.string("container_name"), c.string("container_number")).joinToString(" · ").ifBlank { fallbackLabel },
            packed = items.sumOf { it.double("packed_quantity") ?: 0.0 },
            total = items.sumOf { it.double("picked_quantity") ?: it.double("requested_quantity") ?: 0.0 },
        )
    }.orEmpty()

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

    val pickTitle = stringResource(R.string.pick_title)
    val pickSubtitleList = stringResource(R.string.pick_subtitle_list)
    val emDash = stringResource(R.string.display_empty)
    val errEmptyPickList = stringResource(R.string.pick_error_empty_pick_list)
    val errScanTagsFirst = stringResource(R.string.common_error_scan_tags_first)
    val msgConfirmed = stringResource(R.string.common_success_confirmed)
    val msgPickingSaved = stringResource(R.string.pick_success_picking_saved)
    val msgPickingCompleted = stringResource(R.string.pick_success_picking_completed)
    val msgPackCutSaved = stringResource(R.string.pick_success_pack_cut_saved)
    val fallbackContainer = stringResource(R.string.pick_fallback_container)
    val fallbackProduct = stringResource(R.string.pick_fallback_product)
    val packCutContainerLabel = stringResource(R.string.pick_pack_cut_container)
    val colProduct = stringResource(R.string.common_col_product)
    val colVar = stringResource(R.string.common_col_variation)
    val colRoll = stringResource(R.string.common_col_roll)
    val colStatus = stringResource(R.string.common_col_status)
    val colPickNumber = stringResource(R.string.pick_col_number)
    val colWarehouse = stringResource(R.string.label_warehouse)
    val colDate = stringResource(R.string.label_date)
    val colRequested = stringResource(R.string.pick_col_requested)
    val colPicked = stringResource(R.string.pick_col_picked)
    val labelPickedLength = stringResource(R.string.pick_label_picked_length)
    val labelPickedQty = stringResource(R.string.pick_label_picked_qty)

    var pickList by remember { mutableStateOf<JsonObject?>(null) }
    var pickLoading by remember { mutableStateOf(false) }
    var pickLoadedId by remember { mutableLongStateOf(-1L) }
    val lineEdits = remember { mutableStateListOf<PickLineEdit>() }
    var lineHighlights by remember { mutableStateOf<Map<String, ScanMatchStatus>>(emptyMap()) }
    val containerEdits = remember { mutableStateListOf<ContainerPackEdit>() }
    // Values as last loaded from the server, keyed per editable field; a field whose value
    // differs from this is an unsaved local edit.
    val pickBaseline = remember { mutableMapOf<String, String>() }
    var detailTab by remember { mutableIntStateOf(0) }
    // Confirm-by-exception: taps and scans confirm on the server; manual quantity entry is optional.
    var showAdjust by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var barcodeInput by remember { mutableStateOf("") }
    val handledScans = remember { mutableMapOf<String, Long>() }
    val msgSaved = stringResource(R.string.pick_success_confirmed)
    val scanNotFound = stringResource(R.string.pick_scan_not_found)

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
    val pickIndexColumns = listOf(
        IndexColumnSpec(colPickNumber, 1.1f, { it.string("pick_list_number") ?: "" }) {
            TableCell.Text(it.string("pick_list_number") ?: emDash, bold = true)
        },
        IndexColumnSpec(colStatus, 0.85f, { it.string("pick_status") ?: "" }) {
            TableCell.Status(it.string("pick_status"))
        },
        IndexColumnSpec(colWarehouse, 1f, { it.obj("warehouse")?.string("name") ?: "" }) {
            TableCell.Text(it.obj("warehouse")?.string("name") ?: emDash)
        },
        IndexColumnSpec(colDate, 0.75f, { it.string("pick_date") ?: "" }) {
            TableCell.Date(it.string("pick_date"))
        },
    )

    /**
     * Rebuild the editable lines from [body]. With [keepLocalEdits] (the 10 s background refresh),
     * any field the user changed since the last server load is carried over instead of being
     * reset to the server value — otherwise unsaved input snapped back to 0 every refresh.
     */
    fun applyPickList(body: JsonObject?, keepLocalEdits: Boolean = false) {
        val resolved = unwrapPickList(body)
        val localEdits = if (keepLocalEdits) {
            buildMap {
                lineEdits.forEach { l ->
                    if (l.picked != pickBaseline["line:${l.itemId}:picked"]) put("line:${l.itemId}:picked", l.picked)
                    if (l.packed != pickBaseline["line:${l.itemId}:packed"]) put("line:${l.itemId}:packed", l.packed)
                    if (l.cutLengths != pickBaseline["line:${l.itemId}:cut"]) put("line:${l.itemId}:cut", l.cutLengths)
                }
                containerEdits.forEach { c ->
                    if (c.packed != pickBaseline["ci:${c.containerItemId}:packed"]) put("ci:${c.containerItemId}:packed", c.packed)
                }
            }
        } else {
            emptyMap()
        }
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
                    ?: fallbackContainer
                container.array("container_items")?.forEach { ciEl ->
                    val ci = ciEl.asJsonObject
                    val product = ci.obj("product")?.string("name") ?: fallbackProduct
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
        pickBaseline.clear()
        lineEdits.forEach { l ->
            pickBaseline["line:${l.itemId}:picked"] = l.picked
            pickBaseline["line:${l.itemId}:packed"] = l.packed
            pickBaseline["line:${l.itemId}:cut"] = l.cutLengths
        }
        containerEdits.forEach { c -> pickBaseline["ci:${c.containerItemId}:packed"] = c.packed }
        if (localEdits.isNotEmpty()) {
            lineEdits.forEachIndexed { i, l ->
                lineEdits[i] = l.copy(
                    picked = localEdits["line:${l.itemId}:picked"] ?: l.picked,
                    packed = localEdits["line:${l.itemId}:packed"] ?: l.packed,
                    cutLengths = localEdits["line:${l.itemId}:cut"] ?: l.cutLengths,
                )
            }
            containerEdits.forEachIndexed { i, c ->
                localEdits["ci:${c.containerItemId}:packed"]?.let { containerEdits[i] = c.copy(packed = it) }
            }
        }
        // Only an explicit load/save moves to the pack tab; a background refresh never switches tabs.
        if (keepLocalEdits) return
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

    fun beginPickDetail(id: Long) {
        message = null
        pickList = null
        lineEdits.clear()
        containerEdits.clear()
        pickLoading = true
        pickLoadedId = -1L
        step = PickStep.Detail(id)
    }

    suspend fun reloadPick(id: Long) {
        val res = container.api.getPickList(id)
        if (res.isSuccessful) applyPickList(WorkflowJson.envelopeObject(res))
    }

    /** One-tap confirm on the server, then reload; [onDone] sees the refreshed pick list. */
    fun runConfirm(pickId: Long, request: PickConfirmRequest, onDone: (JsonObject?) -> Unit = {}) {
        scope.launchWorkflow(
            setLoading = { actionLoading = it },
            onError = { message = it },
            onSuccess = { message = it },
        ) {
            val res = container.api.confirmPickList(pickId, request)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
            container.workflowDraftStore.clear("pick_lines_$pickId")
            reloadPick(pickId)
            onDone(pickList)
            res.body()?.message ?: msgSaved
        }
    }

    fun openPick(id: Long) {
        container.rfidManager.clearScannedTags()
        beginPickDetail(id)
        scope.launchWorkflow(
            setLoading = { },
            onError = { message = it; pickLoading = false },
        ) {
            val res = container.api.getPickList(id)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            applyPickList(WorkflowJson.envelopeObject(res) ?: error(errEmptyPickList))
            pickLoadedId = id
            pickLoading = false
            mergePickDraft(id)
            null
        }
    }

    LaunchedEffect(Unit) {
        container.rfidManager.scannedTags.collect { list ->
            val current = step
            val pickId = (current as? PickStep.Detail)?.id ?: 0L
            if (current is PickStep.Detail && pickLoadedId != pickId) return@collect
            val fresh = list.filter { tag ->
                // A barcode counts every time it is read; an RFID tag once per pick list.
                val stamp = if (tag.type == RfidManager.ScanType.BARCODE) tag.lastSeenAt else 0L
                val key = "$pickId:${tag.epc}"
                if (handledScans[key] == stamp) false else { handledScans[key] = stamp; true }
            }.reversed()
            if (fresh.isEmpty()) return@collect
            if (current !is PickStep.Detail) {
                val code = fresh.last().epc
                val res = runCatching { container.api.lookupPickListByScan(PickScanLookupRequest(code)) }.getOrNull()
                val id = res?.takeIf { it.isSuccessful }?.let { WorkflowJson.envelopeObject(it)?.long("id") }
                if (id != null) openPick(id) else message = scanNotFound.format(code)
                return@collect
            }
            val stepName = if (detailTab == 0) "pick" else "pack"
            fresh.forEach { tag ->
                val res = runCatching { container.api.scanPickList(pickId, PickScanRequest(tag.epc, stepName)) }.getOrNull()
                scanMessage = when {
                    res == null -> tag.epc
                    res.isSuccessful -> "${tag.epc}: ${res.body()?.message ?: msgSaved}"
                    else -> "${tag.epc}: ${ApiErrorParser.httpMessage(res)}"
                }
            }
            runCatching { reloadPick(pickId) }
            if (detailTab == 1 && pickList?.string("pick_status") == "completed") {
                step = PickStep.List
                liveList.refresh()
            }
        }
    }

    LaunchedEffect((step as? PickStep.Detail)?.id) {
        val id = (step as? PickStep.Detail)?.id ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000)
            if ((step as? PickStep.Detail)?.id != id) break
            if (pickLoading || pickLoadedId != id) continue
            runCatching {
                val res = container.api.getPickList(id)
                if (res.isSuccessful) applyPickList(WorkflowJson.envelopeObject(res), keepLocalEdits = true)
            }
        }
    }

    ErpScaffold(
        title = when (step) {
            PickStep.List -> pickTitle
            is PickStep.Detail -> pickList?.string("pick_list_number") ?: pickTitle
        },
        subtitle = when (step) {
            PickStep.List -> pickSubtitleList
            is PickStep.Detail -> UiStrings.apiStatus(pickList?.string("pick_status"))
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
                StatusBanner(it, isError = StatusMessage.looksLikeError(it))
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
                    emptyText = stringResource(R.string.pick_empty_list),
                    searchPlaceholder = stringResource(R.string.pick_search_placeholder),
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
                if (pickLoading || pickLoadedId != pickId) {
                    PickListDetailSkeleton(Modifier.weight(1f))
                } else {
                TabRow(detailTab) {
                    Tab(selected = detailTab == 0, onClick = { detailTab = 0 }, text = { Text(stringResource(R.string.pick_tab_pick)) })
                    Tab(selected = detailTab == 1, onClick = { detailTab = 1 }, text = { Text(stringResource(R.string.pick_tab_pack_cut)) })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detailTab == 0) {
                        val lineTitle: (PickLineEdit) -> String = { line ->
                            buildString {
                                append(line.productLabel)
                                if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                line.rollNumber?.let { append(" · #").append(it) }
                            }
                        }
                        Text(stringResource(R.string.pick_scan_hint_pick), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        scanMessage?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        ErpPrimaryButton(text = stringResource(R.string.pick_btn_pick_all_complete), loading = actionLoading, onClick = {
                            runConfirm(pickId, PickConfirmRequest(scope = "pick_all", complete = true)) { detailTab = 1 }
                        })
                        lineEdits.forEach { line ->
                            val picked = line.picked.toDoubleOrNull() ?: 0.0
                            val full = line.requested > 0.0 && picked >= line.requested
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(lineTitle(line), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        stringResource(
                                            R.string.pick_qty_progress,
                                            qtyWithUnit(line.picked, line.quantityUnitSuffix, line.isRoll),
                                            qtyWithUnit(DisplayFormat.qty(line.requested), line.quantityUnitSuffix, line.isRoll),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (full) {
                                    TextButton(enabled = !actionLoading, onClick = {
                                        runConfirm(pickId, PickConfirmRequest(scope = "pick_line", ids = listOf(line.itemId), quantity = 0.0))
                                    }) { Text(stringResource(R.string.pick_btn_undo)) }
                                } else {
                                    OutlinedButton(enabled = !actionLoading, onClick = {
                                        runConfirm(pickId, PickConfirmRequest(scope = "pick_line", ids = listOf(line.itemId)))
                                    }) { Text(stringResource(R.string.pick_btn_pick_line)) }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = { barcodeInput = it },
                            label = { Text(stringResource(R.string.label_barcode)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                barcodeInput.trim().takeIf { it.isNotBlank() }?.let { container.rfidManager.recordWorkflowBarcode(it) }
                                barcodeInput = ""
                            }),
                        )
                        TextButton(onClick = { showAdjust = !showAdjust }) {
                            Text(stringResource(if (showAdjust) R.string.pick_toggle_adjust_hide else R.string.pick_toggle_adjust))
                        }
                        if (showAdjust) {
                            lineEdits.forEachIndexed { index, line ->
                                val pickLabel = if (line.isRoll) {
                                    WorkflowJson.rollLengthLabel(line.quantityUnitSuffix, labelPickedLength)
                                } else {
                                    labelPickedQty
                                }
                                QtyField(
                                    value = line.picked,
                                    onValueChange = { v -> lineEdits[index] = line.copy(picked = v) },
                                    label = stringResource(
                                        R.string.pick_field_label_product,
                                        pickLabel,
                                        buildString {
                                            append(line.productLabel)
                                            if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                            line.rollNumber?.let { append(stringResource(R.string.pick_line_roll_suffix, it)) }
                                        },
                                    ),
                                    fillValue = line.requested,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ErpPrimaryButton(text = stringResource(R.string.pick_btn_save_picking), loading = actionLoading, onClick = {
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
                                    msgPickingSaved
                                }
                            })
                            ErpPrimaryButton(text = stringResource(R.string.pick_btn_complete_picking), loading = actionLoading, onClick = {
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
                                    msgPickingCompleted
                                }
                            })
                        }
                    } else {
                        Text(stringResource(R.string.pick_scan_hint_pack), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        scanMessage?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        val backToListWhenDone: (JsonObject?) -> Unit = { pl ->
                            if (pl?.string("pick_status") == "completed") {
                                step = PickStep.List
                                liveList.refresh()
                            }
                        }
                        ErpPrimaryButton(text = stringResource(R.string.pick_btn_pack_cut_all), loading = actionLoading, onClick = {
                            runConfirm(pickId, PickConfirmRequest(scope = "pack_all"), backToListWhenDone)
                        })
                        val bals = balRows(pickList, fallbackContainer)
                        if (bals.isNotEmpty()) {
                            Text(stringResource(R.string.pick_section_bals), style = MaterialTheme.typography.labelMedium)
                            bals.forEach { bal ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(bal.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(stringResource(R.string.pick_qty_progress, DisplayFormat.qty(bal.packed), DisplayFormat.qty(bal.total)),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (bal.total > 0.0 && bal.packed >= bal.total) {
                                        Text(stringResource(R.string.pick_done), color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        OutlinedButton(enabled = !actionLoading, onClick = {
                                            runConfirm(pickId, PickConfirmRequest(scope = "pack_container", ids = listOf(bal.id)), backToListWhenDone)
                                        }) { Text(stringResource(R.string.pick_btn_pack_bal)) }
                                    }
                                }
                            }
                        }
                        val rolls = lineEdits.filter { it.isRoll }
                        if (rolls.isNotEmpty()) {
                            Text(stringResource(R.string.pick_section_rolls), style = MaterialTheme.typography.labelMedium)
                            rolls.forEach { line ->
                                val cut = line.cutLengths.split(",").mapNotNull { it.trim().toDoubleOrNull() }.sum()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(buildString {
                                            append(line.productLabel)
                                            if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                            line.rollNumber?.let { append(" · #").append(it) }
                                        }, style = MaterialTheme.typography.bodyMedium)
                                        Text(line.cutLengths.ifBlank { emDash }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (line.requested > 0.0 && cut >= line.requested) {
                                        Text(stringResource(R.string.pick_done), color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        OutlinedButton(enabled = !actionLoading, onClick = {
                                            runConfirm(pickId, PickConfirmRequest(scope = "cut_roll", ids = listOf(line.itemId)), backToListWhenDone)
                                        }) { Text(stringResource(R.string.pick_btn_cut_roll)) }
                                    }
                                }
                            }
                        }
                        val loose = lineEdits.filter { !it.isRoll && !it.hasContainerAssignments }
                        if (loose.isNotEmpty()) {
                            Text(stringResource(R.string.pick_section_loose), style = MaterialTheme.typography.labelMedium)
                            loose.forEach { line ->
                                val packed = line.packed.toDoubleOrNull() ?: 0.0
                                val picked = line.picked.toDoubleOrNull() ?: 0.0
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(buildString {
                                            append(line.productLabel)
                                            if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                        }, style = MaterialTheme.typography.bodyMedium)
                                        Text(stringResource(R.string.pick_qty_progress, line.packed, line.picked),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (picked > 0.0 && packed >= picked) {
                                        Text(stringResource(R.string.pick_done), color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        OutlinedButton(enabled = !actionLoading, onClick = {
                                            scope.launchWorkflow(setLoading = { actionLoading = it }, onError = { message = it }, onSuccess = { message = it }) {
                                                val res = container.api.updatePickListPackCut(
                                                    pickId,
                                                    UpdatePackCutRequest(packed_quantities = mapOf(pickId.toString() to mapOf(line.itemId.toString() to picked))),
                                                )
                                                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                                                reloadPick(pickId)
                                                backToListWhenDone(pickList)
                                                res.body()?.message ?: msgSaved
                                            }
                                        }) { Text(stringResource(R.string.pick_btn_pack_line)) }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = { barcodeInput = it },
                            label = { Text(stringResource(R.string.label_barcode)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                barcodeInput.trim().takeIf { it.isNotBlank() }?.let { container.rfidManager.recordWorkflowBarcode(it) }
                                barcodeInput = ""
                            }),
                        )
                        TextButton(onClick = { showAdjust = !showAdjust }) {
                            Text(stringResource(if (showAdjust) R.string.pick_toggle_adjust_hide else R.string.pick_toggle_adjust))
                        }
                        if (showAdjust) {
                            if (containerEdits.isNotEmpty()) {
                                Text(stringResource(R.string.pick_section_container_packing), style = MaterialTheme.typography.labelMedium)
                                WorkflowDataTable(
                                    columns = listOf(
                                        DataTableColumn(stringResource(R.string.pick_col_assignment), 1.4f),
                                        DataTableColumn(colPicked, 0.45f),
                                        DataTableColumn(stringResource(R.string.pick_col_packed), 0.45f),
                                    ),
                                    rows = containerEdits.map { line ->
                                        listOf(
                                            TableCell.Text(line.label),
                                            TableCell.Text(DisplayFormat.qty(line.picked)),
                                            TableCell.Text(line.packed),
                                        )
                                    },
                                    emptyText = stringResource(R.string.pick_empty_container_lines),
                                )
                                containerEdits.forEachIndexed { index, line ->
                                    QtyField(
                                        value = line.packed,
                                        onValueChange = { v -> containerEdits[index] = line.copy(packed = v) },
                                        label = stringResource(R.string.pick_label_packed_container, line.label),
                                        fillValue = line.picked,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            WorkflowDataTable(
                                columns = listOf(
                                    DataTableColumn(colProduct, 0.9f),
                                    DataTableColumn(colVar, 0.5f),
                                    DataTableColumn(colRoll, 0.4f),
                                    DataTableColumn(colPicked, 0.4f),
                                    DataTableColumn(stringResource(R.string.pick_col_pack_cut), 0.5f),
                                ),
                                rows = lineEdits.map { line ->
                                    val rollLabel = line.rollNumber
                                        ?: line.rollLength?.let { DisplayFormat.qty(it) }
                                        ?: if (line.isRoll) emDash else ""
                                    val packCut = when {
                                        line.isRoll -> line.cutLengths.ifBlank { emDash }
                                        line.hasContainerAssignments -> packCutContainerLabel
                                        else -> line.packed
                                    }
                                    listOf(
                                        TableCell.Text(line.productLabel),
                                        TableCell.Text(line.variationLabel.ifBlank { emDash }),
                                        TableCell.Text(rollLabel.ifBlank { emDash }),
                                        TableCell.Text(qtyWithUnit(line.picked, line.quantityUnitSuffix, line.isRoll)),
                                        TableCell.Text(packCut),
                                    )
                                },
                                emptyText = stringResource(R.string.pick_empty_pack_lines),
                            )
                            lineEdits.forEachIndexed { index, line ->
                                when {
                                    line.isRoll -> {
                                        val unit = line.quantityUnitSuffix ?: "m"
                                        val productPart = buildString {
                                            append(line.productLabel)
                                            if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                            line.rollNumber?.let { append(stringResource(R.string.pick_line_roll_suffix, it)) }
                                        }
                                        OutlinedTextField(
                                            line.cutLengths,
                                            { v -> lineEdits[index] = line.copy(cutLengths = v) },
                                            label = {
                                                Text(stringResource(R.string.pick_label_cut_lengths, unit, productPart))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                        )
                                        Text(
                                            stringResource(R.string.pick_cut_lengths_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    !line.hasContainerAssignments -> {
                                        val productPart = buildString {
                                            append(line.productLabel)
                                            if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                        }
                                        QtyField(
                                            value = line.packed,
                                            onValueChange = { v -> lineEdits[index] = line.copy(packed = v) },
                                            label = stringResource(R.string.pick_label_packed_qty, productPart),
                                            fillValue = line.picked.toDoubleOrNull(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            ErpPrimaryButton(text = stringResource(R.string.pick_btn_save_pack_cut), loading = actionLoading, onClick = {
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
                                    // Pack & cut is the last step: back to the list (refreshed, so a finished
                                    // pick drops off); the saved message shows there.
                                    container.workflowDraftStore.clear("pick_lines_$pickId")
                                    step = PickStep.List
                                    liveList.refresh()
                                    msgPackCutSaved
                                }
                            })
                        }
                    }
                }
                }
            }
        }
    }
}
