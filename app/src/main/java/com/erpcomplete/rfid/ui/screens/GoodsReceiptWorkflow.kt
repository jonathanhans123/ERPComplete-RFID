package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.CreateGoodsReceiptItem
import com.erpcomplete.rfid.data.remote.CreateGoodsReceiptRequest
import com.erpcomplete.rfid.data.remote.WorkflowScanRequest
import com.erpcomplete.rfid.ui.components.DataTableColumn
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.IndexColumnSpec
import com.erpcomplete.rfid.ui.components.JsonIndexListTable
import com.erpcomplete.rfid.ui.components.GoodsReceiptDetailSkeleton
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
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
import com.erpcomplete.rfid.ui.components.shouldIncrementQtyOnScan
import com.erpcomplete.rfid.ui.components.rememberScanMatchColors
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.PickerMappers
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.isBenignCancellation
import com.erpcomplete.rfid.util.isBenignCancellationMessage
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.workspaceContext
import com.erpcomplete.rfid.util.WorkflowJson.array
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.nestedItems
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.rollDisplayLabel
import com.erpcomplete.rfid.util.WorkflowJson.rollLengthLabel
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

private sealed class GrStep {
    data object List : GrStep()
    data object Create : GrStep()
    data class Detail(val id: Long) : GrStep()
}

private fun GrStep.encode(): String = when (this) {
    GrStep.List -> "list"
    GrStep.Create -> "create"
    is GrStep.Detail -> "detail:$id"
}

private fun decodeGrStep(raw: String): GrStep {
    val parts = raw.split(':', limit = 2)
    return when (parts[0]) {
        "create" -> GrStep.Create
        "detail" -> GrStep.Detail(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        else -> GrStep.List
    }
}

private val GrStepSaver = androidx.compose.runtime.saveable.Saver<GrStep, String>(
    save = { it.encode() },
    restore = { decodeGrStep(it) },
)

private data class GrLineDraft(
    val sourceItemId: Long?,
    val productId: Long,
    val variationValueId: Long?,
    val variationLabel: String,
    val productName: String,
    val productSku: String?,
    val orderedQty: Double,
    val previouslyReceived: Double,
    val remainingQty: Double,
    val isRollProduct: Boolean,
    val rollNumber: String? = null,
    val rollLengthHint: Double? = null,
    val packingListItemId: Long? = null,
    val quantityUnitSuffix: String? = null,
    var acceptedQty: String,
    var rejectedQty: String,
    var qualityStatus: String,
)

private fun parseGrLineItem(
    item: JsonObject,
    rollNumber: String? = null,
    rollLengthHint: Double? = null,
    packingListItemId: Long? = null,
): GrLineDraft {
    val rem = item.double("remaining_quantity") ?: 0.0
    val isRoll = item.get("is_roll_product")?.asBoolean == true || item.isRollStockLine()
    val unit = item.quantityUnitSuffix()
    return GrLineDraft(
        sourceItemId = item.long("id"),
        productId = item.long("product_id") ?: 0L,
        variationValueId = item.long("variation_value_id"),
        variationLabel = item.variationLabel(),
        productName = item.string("product_name") ?: "Product",
        productSku = item.string("product_sku"),
        orderedQty = item.double("ordered_quantity") ?: 0.0,
        previouslyReceived = item.double("previously_received") ?: 0.0,
        remainingQty = rem,
        isRollProduct = isRoll,
        rollNumber = rollNumber ?: item.string("roll_number"),
        rollLengthHint = rollLengthHint ?: item.double("roll_length"),
        packingListItemId = packingListItemId,
        quantityUnitSuffix = unit,
        acceptedQty = if (isRoll) "" else DisplayFormat.qty(rem),
        rejectedQty = "0",
        qualityStatus = "approved",
    )
}

private fun parseGrSourceLines(items: List<JsonObject>): List<GrLineDraft> {
    val lines = mutableListOf<GrLineDraft>()
    items.forEach { item ->
        val isRoll = item.get("is_roll_product")?.asBoolean == true || item.isRollStockLine()
        val packingItems = item.array("packing_list_items")
        when {
            isRoll && packingItems != null && packingItems.size() > 0 -> {
                packingItems.forEach { el ->
                    val pi = el.asJsonObject
                    lines.add(
                        parseGrLineItem(
                            item = item,
                            rollNumber = pi.string("roll_number"),
                            rollLengthHint = pi.double("length") ?: pi.double("roll_length"),
                            packingListItemId = pi.long("id"),
                        ),
                    )
                }
            }
            isRoll && !item.string("roll_number").isNullOrBlank() -> {
                lines.add(parseGrLineItem(item))
            }
            else -> lines.add(parseGrLineItem(item))
        }
    }
    return lines
}

private data class GrDetailHeaderEdit(
    val receiptDate: String,
    val deliveryNote: String,
    val vehicleNumber: String,
    val driverName: String,
    val receivedById: Long,
    val notes: String,
)

private fun JsonObject.toGrDetailHeaderEdit(): GrDetailHeaderEdit = GrDetailHeaderEdit(
    receiptDate = string("receipt_date") ?: LocalDate.now().toString(),
    deliveryNote = string("delivery_note_number").orEmpty(),
    vehicleNumber = string("vehicle_number").orEmpty(),
    driverName = string("driver_name").orEmpty(),
    receivedById = long("received_by") ?: 0L,
    notes = string("notes").orEmpty(),
)

private fun JsonObject.detailReceivedByPicker(): PickerOption? {
    val userId = long("received_by") ?: return null
    val user = obj("received_by_user")
    val label = user?.string("name") ?: "User #$userId"
    return PickerOption(userId, label, user?.string("email"))
}

private data class GrDetailLineEdit(
    val goodsReceiptItemId: Long,
    val productId: Long,
    val variationValueId: Long?,
    val variationLabel: String,
    val productName: String,
    val productSku: String?,
    val rollNumber: String?,
    val rollLengthHint: Double?,
    val isRollProduct: Boolean,
    val quantityUnitSuffix: String?,
    val orderedQty: Double,
    var acceptedQty: String,
    var rejectedQty: String,
)

private fun detailItemToEdit(item: JsonObject): GrDetailLineEdit? {
    val id = item.long("id") ?: return null
    val isRoll = item.isRollStockLine()
    val accepted = item.double("accepted_quantity") ?: 0.0
    return GrDetailLineEdit(
        goodsReceiptItemId = id,
        productId = item.long("product_id") ?: 0L,
        variationValueId = item.long("variation_value_id"),
        variationLabel = item.variationLabel(),
        productName = item.productName(),
        productSku = item.string("product_sku"),
        rollNumber = item.string("roll_number"),
        rollLengthHint = item.double("roll_length"),
        isRollProduct = isRoll,
        quantityUnitSuffix = item.quantityUnitSuffix(),
        orderedQty = item.double("ordered_quantity") ?: 0.0,
        acceptedQty = if (isRoll && accepted <= 0) "" else DisplayFormat.qty(accepted),
        rejectedQty = DisplayFormat.qty(item.double("rejected_quantity") ?: 0.0),
    )
}

private fun buildGrUpdateRequest(
    gr: JsonObject,
    edits: List<GrDetailLineEdit>,
    header: GrDetailHeaderEdit,
): CreateGoodsReceiptRequest {
    val items = edits.map { line ->
        val acc = line.acceptedQty.toDoubleOrNull() ?: 0.0
        val rej = line.rejectedQty.toDoubleOrNull() ?: 0.0
        CreateGoodsReceiptItem(
            goods_receipt_item_id = line.goodsReceiptItemId,
            product_id = line.productId,
            product_name = line.productName,
            product_sku = line.productSku,
            ordered_quantity = line.orderedQty,
            received_quantity = acc + rej,
            accepted_quantity = acc,
            rejected_quantity = rej,
            variation_value_id = line.variationValueId,
            quality_status = when {
                acc > 0 && rej > 0 -> "approved"
                rej > 0 -> "rejected"
                else -> "approved"
            },
            roll_number = line.rollNumber,
            roll_length = line.acceptedQty.toDoubleOrNull() ?: line.rollLengthHint,
        )
    }
    return CreateGoodsReceiptRequest(
        source_type = gr.string("source_type") ?: "purchase_order",
        source_id = gr.long("source_id") ?: 0L,
        supplier_id = gr.long("supplier_id"),
        warehouse_id = gr.long("warehouse_id") ?: 0L,
        receipt_date = header.receiptDate,
        received_by = header.receivedById,
        delivery_note_number = header.deliveryNote.ifBlank { null },
        vehicle_number = header.vehicleNumber.ifBlank { null },
        driver_name = header.driverName.ifBlank { null },
        notes = header.notes.ifBlank { null },
        total_items = items.size,
        total_received_quantity = items.sumOf { it.received_quantity ?: 0.0 },
        items = items,
    )
}

private data class GrSaveResult(
    val saved: JsonObject,
    val putawayTaskId: Long?,
    val message: String,
)

private suspend fun saveGoodsReceiptDetail(
    container: AppContainer,
    grId: Long,
    gr: JsonObject,
    lineEdits: List<GrDetailLineEdit>,
    header: GrDetailHeaderEdit,
): GrSaveResult {
    val body = buildGrUpdateRequest(gr, lineEdits, header)
    val res = container.workflowApi.executeOrQueue(
        endpoint = "goods-receipts/$grId",
        method = "PUT",
        body = body,
    ) { container.api.updateGoodsReceipt(grId, body) }
    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res, authenticated = true))
    val saved = WorkflowJson.envelopeObject(res) ?: gr
    container.workflowDraftStore.clear("gr_lines_$grId")
    return GrSaveResult(
        saved = saved,
        putawayTaskId = container.resolvePutawayTaskId(grId, saved),
        message = res.body()?.message ?: "Goods receipt saved",
    )
}

private fun applyGrSaveToUi(
    result: GrSaveResult,
    setDetail: (JsonObject) -> Unit,
    setPutawayTaskId: (Long?) -> Unit,
    setHeaderDate: (String) -> Unit,
    setHeaderDeliveryNote: (String) -> Unit,
    setHeaderVehicle: (String) -> Unit,
    setHeaderDriver: (String) -> Unit,
    setHeaderNotes: (String) -> Unit,
    setHeaderReceivedBy: (PickerOption?) -> Unit,
    reloadLineEdits: () -> Unit,
) {
    setDetail(result.saved)
    setPutawayTaskId(result.putawayTaskId)
    val h = result.saved.toGrDetailHeaderEdit()
    setHeaderDate(h.receiptDate)
    setHeaderDeliveryNote(h.deliveryNote)
    setHeaderVehicle(h.vehicleNumber)
    setHeaderDriver(h.driverName)
    setHeaderNotes(h.notes)
    setHeaderReceivedBy(result.saved.detailReceivedByPicker())
    reloadLineEdits()
}

private suspend fun AppContainer.resolvePutawayTaskId(grId: Long, created: JsonObject?): Long? {
    created?.firstPutawayTaskId()?.let { return it }
    val whId = workspaceContext().warehouseId
    val res = api.listPutawayTasks(goodsReceiptId = grId, warehouseId = whId, perPage = 5)
    if (!res.isSuccessful) return null
    return WorkflowJson.envelopeList(res).firstOrNull()?.long("id")
}

private data class GrSourceLinesResult(
    val header: JsonObject?,
    val lines: List<GrLineDraft>,
)

private suspend fun AppContainer.fetchGoodsReceiptSourceLines(
    sourceType: String,
    sourceId: Long,
): GrSourceLinesResult {
    val root = when (sourceType) {
        "stock_transfer" -> {
            val res = api.getStockTransferItemsForGr(sourceId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            res.body()?.asJsonObject ?: error("Empty response")
        }
        "sales_return" -> {
            val res = api.getSalesReturnItemsForGr(sourceId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            res.body()?.asJsonObject ?: error("Empty response")
        }
        else -> {
            val res = api.getPurchaseOrderItemsForGr(sourceId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            res.body()?.asJsonObject ?: error("Empty response")
        }
    }
    if (root.get("success")?.asBoolean == false) {
        error(root.get("message")?.asString ?: "Failed to load lines")
    }
    val header = when (sourceType) {
        "stock_transfer" -> root.getAsJsonObject("stockTransfer")
        "sales_return" -> root.getAsJsonObject("salesReturn")
        else -> root.getAsJsonObject("purchaseOrder")
    }
    val lines = root.getAsJsonArray("items")
        ?.mapNotNull { it.takeIf { el -> el.isJsonObject }?.asJsonObject }
        ?.let { parseGrSourceLines(it) }
        ?: emptyList()
    return GrSourceLinesResult(header, lines)
}

private fun GrLineDraft.deriveQualityStatus(): String = when {
    (acceptedQty.toDoubleOrNull() ?: 0.0) > 0 && (rejectedQty.toDoubleOrNull() ?: 0.0) > 0 -> "approved"
    (rejectedQty.toDoubleOrNull() ?: 0.0) > 0 -> "rejected"
    else -> qualityStatus.ifBlank { "approved" }
}

private fun JsonObject.firstPutawayTaskId(): Long? =
    array("putaway_tasks")?.firstOrNull()?.asJsonObject?.long("id")

@Composable
fun ReceiveScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPutaway: (Long) -> Unit = {},
) {
    var step by rememberSaveable(stateSaver = GrStepSaver) { mutableStateOf(GrStep.List) }
    var message by remember { mutableStateOf<String?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    val tags by container.rfidManager.scannedTags.collectAsState()
    val scope = rememberCoroutineScope()
    val scanColors = rememberScanMatchColors()

    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailLoadedId by remember { mutableLongStateOf(-1L) }
    var createTab by remember { mutableIntStateOf(0) }
    var sourceType by remember { mutableStateOf("purchase_order") }
    var selectedSource by remember { mutableStateOf<PickerOption?>(null) }
    var selectedWarehouse by remember { mutableStateOf<PickerOption?>(null) }
    var selectedReceivedBy by remember { mutableStateOf<PickerOption?>(null) }
    var receiptDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var deliveryNote by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var receiptNotes by remember { mutableStateOf("") }
    var createdPutawayTaskId by remember { mutableStateOf<Long?>(null) }
    val lineDrafts = remember { mutableStateListOf<GrLineDraft>() }
    var lineHighlights by remember { mutableStateOf<Map<String, ScanMatchStatus>>(emptyMap()) }
    var detailLineHighlights by remember { mutableStateOf<Map<String, ScanMatchStatus>>(emptyMap()) }
    val detailLineEdits = remember { mutableStateListOf<GrDetailLineEdit>() }
    var detailEditTab by remember { mutableIntStateOf(0) }
    var detailHeaderDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var detailHeaderDeliveryNote by remember { mutableStateOf("") }
    var detailHeaderVehicle by remember { mutableStateOf("") }
    var detailHeaderDriver by remember { mutableStateOf("") }
    var detailHeaderNotes by remember { mutableStateOf("") }
    var detailHeaderReceivedBy by remember { mutableStateOf<PickerOption?>(null) }
    var sourceHeader by remember { mutableStateOf<JsonObject?>(null) }

    var sourcePickerOpen by remember { mutableStateOf(false) }
    var whPickerOpen by remember { mutableStateOf(false) }
    var userPickerOpen by remember { mutableStateOf(false) }
    var sourceOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var whOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var userOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var sourceSearchQuery by remember { mutableStateOf("") }
    var whSearchQuery by remember { mutableStateOf("") }
    var userSearchQuery by remember { mutableStateOf("") }
    var pickerLoading by remember { mutableStateOf(false) }
    var linesLoading by remember { mutableStateOf(false) }

    val workspaceWarehouseId by container.authStore.warehouseId.collectAsState(initial = null)
    val workspaceWarehouseName by container.authStore.warehouseName.collectAsState(initial = null)
    val workspaceBusinessUnitId by container.authStore.businessUnitId.collectAsState(initial = null)
    val workspaceTeamId by container.authStore.teamId.collectAsState(initial = null)
    val workspaceScopeKey = "$workspaceBusinessUnitId|$workspaceTeamId|$workspaceWarehouseId"

    val liveList = rememberWorkflowLiveList(enabled = step is GrStep.List) { page ->
        val whId = container.workspaceContext().warehouseId
        val res = container.api.listGoodsReceipts(warehouseId = whId, page = page, perPage = 50)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        WorkflowJson.envelopePage(res, page)
    }
    val listSortSearch = rememberTableSortSearch()
    val grIndexColumns = remember {
        listOf(
            IndexColumnSpec("GR #", 1.25f, { it.string("goods_receipt_number") ?: "" }) {
                TableCell.Text(it.string("goods_receipt_number") ?: "—", bold = true, mono = true)
            },
            IndexColumnSpec("Status", 0.75f, {
                it.string("receipt_status") ?: it.string("status") ?: ""
            }) {
                TableCell.Status(it.string("receipt_status") ?: it.string("status"))
            },
            IndexColumnSpec("Warehouse", 1.05f, { it.obj("warehouse")?.string("name") ?: "" }) {
                TableCell.Text(it.obj("warehouse")?.string("name") ?: "—")
            },
            IndexColumnSpec("Date", 0.8f, { it.string("receipt_date") ?: "" }) {
                TableCell.Date(it.string("receipt_date"))
            },
        )
    }

    LaunchedEffect(workspaceScopeKey, step) {
        if (step is GrStep.Create) {
            selectedSource = null
            sourceHeader = null
            lineDrafts.clear()
        }
    }

    LaunchedEffect(step) {
        if (step is GrStep.Create && selectedReceivedBy == null) {
            runCatching {
                val res = container.api.currentUser()
                if (res.isSuccessful) {
                    val user = res.body()?.data
                    user?.id?.let { id ->
                        selectedReceivedBy = PickerOption(id, user.name ?: "Me", user.email)
                    }
                }
            }
        }
    }

    suspend fun applyGrDetail(gr: JsonObject, id: Long) {
        detail = gr
        detailLoadedId = id
        detailLoading = false
        val header = gr.toGrDetailHeaderEdit()
        detailHeaderDate = header.receiptDate
        detailHeaderDeliveryNote = header.deliveryNote
        detailHeaderVehicle = header.vehicleNumber
        detailHeaderDriver = header.driverName
        detailHeaderNotes = header.notes
        detailHeaderReceivedBy = gr.detailReceivedByPicker()
        createdPutawayTaskId = container.resolvePutawayTaskId(id, gr)
    }

    fun beginGrDetail(id: Long) {
        message = null
        detail = null
        detailLineEdits.clear()
        detailEditTab = 0
        detailLoading = true
        detailLoadedId = -1L
        step = GrStep.Detail(id)
    }

    LaunchedEffect((step as? GrStep.Detail)?.id) {
        val id = (step as? GrStep.Detail)?.id ?: return@LaunchedEffect
        if (detailLoadedId != id) {
            detailLoading = true
            detail = null
            detailLineEdits.clear()
            runCatching {
                val res = container.api.getGoodsReceipt(id)
                if (!res.isSuccessful) {
                    message = ApiErrorParser.httpMessage(res)
                    detailLoading = false
                    return@LaunchedEffect
                }
                val gr = WorkflowJson.envelopeObject(res) ?: error("Empty goods receipt")
                applyGrDetail(gr, id)
            }.onFailure { e ->
                if (!e.isBenignCancellation()) {
                    message = e.message?.takeIf { it.isNotBlank() } ?: "Could not load receipt"
                }
                detailLoading = false
            }
        }
        while (true) {
            kotlinx.coroutines.delay(10_000)
            if ((step as? GrStep.Detail)?.id != id) break
            if (detailLoading || detailLoadedId != id) continue
            runCatching {
                val res = container.api.getGoodsReceipt(id)
                if (res.isSuccessful) {
                    val gr = WorkflowJson.envelopeObject(res)
                    if (gr != null) {
                        detail = gr
                        createdPutawayTaskId = container.resolvePutawayTaskId(id, gr)
                    }
                }
            }
        }
    }

    LaunchedEffect(detail, step) {
        if (step !is GrStep.Detail) return@LaunchedEffect
        val gr = detail ?: return@LaunchedEffect
        if (detailLineEdits.isNotEmpty()) return@LaunchedEffect
        nestedItems(gr).mapNotNull(::detailItemToEdit).forEach { edit -> detailLineEdits.add(edit) }
        val grId = (step as GrStep.Detail).id
        container.workflowDraftStore.loadBlocking("gr_lines_$grId")?.let { raw ->
            com.google.gson.Gson().fromJson(raw, Array<GrDetailLineEdit>::class.java)?.forEach { draft ->
                val idx = detailLineEdits.indexOfFirst { it.goodsReceiptItemId == draft.goodsReceiptItemId }
                if (idx >= 0) {
                    detailLineEdits[idx] = detailLineEdits[idx].copy(
                        acceptedQty = draft.acceptedQty,
                        rejectedQty = draft.rejectedQty,
                    )
                }
            }
        }
    }

    LaunchedEffect(step, detailLineEdits.size, detailLineEdits.map { "${it.goodsReceiptItemId}:${it.acceptedQty}:${it.rejectedQty}" }) {
        val grId = (step as? GrStep.Detail)?.id ?: return@LaunchedEffect
        delay(500)
        container.workflowDraftStore.save(
            "gr_lines_$grId",
            com.google.gson.Gson().toJson(detailLineEdits.map { it.copy() }),
        )
    }

    LaunchedEffect(step, sourceType, selectedSource?.id) {
        if (step !is GrStep.Create) return@LaunchedEffect
        val sourceId = selectedSource?.id
        if (sourceId == null) {
            sourceHeader = null
            lineDrafts.clear()
            return@LaunchedEffect
        }
        linesLoading = true
        try {
            val result = container.fetchGoodsReceiptSourceLines(sourceType, sourceId)
            sourceHeader = result.header
            lineDrafts.clear()
            lineDrafts.addAll(result.lines)
            if (result.lines.isEmpty()) {
                message = "No remaining lines on this source"
            } else {
                createTab = 1
                message = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isBenignCancellation()) {
                message = e.message
                sourceHeader = null
                lineDrafts.clear()
            }
        } finally {
            linesLoading = false
        }
    }

    LaunchedEffect(sourcePickerOpen) {
        if (sourcePickerOpen) sourceSearchQuery = ""
    }

    LaunchedEffect(whPickerOpen) {
        if (whPickerOpen) whSearchQuery = ""
    }

    LaunchedEffect(userPickerOpen) {
        if (userPickerOpen) userSearchQuery = ""
    }

    LaunchedEffect(sourcePickerOpen, sourceSearchQuery, sourceType, workspaceScopeKey) {
        if (!sourcePickerOpen) return@LaunchedEffect
        pickerLoading = true
        try {
            if (sourceSearchQuery.isNotBlank()) delay(300)
            val search = sourceSearchQuery.ifBlank { null }
            val whId = workspaceWarehouseId?.toLongOrNull()
            sourceOptions = when (sourceType) {
                "stock_transfer" -> {
                    val res = container.api.listStockTransfers(
                        status = "in_transit",
                        search = search,
                        warehouseId = whId,
                        perPage = 60,
                    )
                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                    WorkflowJson.envelopeList(res).mapNotNull(PickerMappers::stockTransfer)
                }
                "sales_return" -> {
                    val res = container.api.listSalesReturns(
                        returnStatus = "received",
                        search = search,
                        warehouseId = whId,
                        perPage = 60,
                    )
                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                    WorkflowJson.envelopeList(res).mapNotNull(PickerMappers::salesReturn)
                }
                else -> {
                    val res = container.api.listPurchaseOrders(search = search, forGoodsReceipt = true, perPage = 60)
                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                    WorkflowJson.envelopeList(res).mapNotNull(PickerMappers::purchaseOrder)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isBenignCancellation()) message = e.message
        } finally {
            pickerLoading = false
        }
    }

    LaunchedEffect(userPickerOpen, userSearchQuery, workspaceScopeKey) {
        if (!userPickerOpen) return@LaunchedEffect
        pickerLoading = true
        try {
            if (userSearchQuery.isNotBlank()) delay(300)
            val res = container.api.listUsers(search = userSearchQuery.ifBlank { null }, forPicker = true, perPage = 60)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            userOptions = WorkflowJson.envelopeList(res).mapNotNull(PickerMappers::user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isBenignCancellation()) message = e.message
        } finally {
            pickerLoading = false
        }
    }

    LaunchedEffect(whPickerOpen, whSearchQuery, workspaceScopeKey) {
        if (!whPickerOpen) return@LaunchedEffect
        pickerLoading = true
        try {
            if (whSearchQuery.isNotBlank()) delay(300)
            val whId = workspaceWarehouseId?.toLongOrNull()
            val res = container.api.listWarehouses(
                search = whSearchQuery.ifBlank { null },
                warehouseId = whId,
                perPage = 60,
            )
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            whOptions = WorkflowJson.envelopeList(res).mapNotNull(PickerMappers::warehouse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isBenignCancellation()) message = e.message
        } finally {
            pickerLoading = false
        }
    }

    val title = when (step) {
        GrStep.List -> "Goods receipt"
        GrStep.Create -> "New goods receipt"
        is GrStep.Detail -> detail?.string("goods_receipt_number") ?: "Goods receipt"
    }

    ErpScaffold(
        title = title,
        subtitle = when (step) {
            GrStep.List -> "Tap a row to open · auto-syncs"
            GrStep.Create -> "Receipt info → items to receive"
            is GrStep.Detail -> DisplayFormat.status(detail?.string("receipt_status") ?: detail?.string("status"))
        },
        onBack = {
            when (step) {
                GrStep.List -> onBack()
                GrStep.Create -> step = GrStep.List
                is GrStep.Detail -> step = GrStep.List
            }
        },
    ) {
        message?.let {
            if (!it.isBenignCancellationMessage()) {
                StatusBanner(it, isError = it.contains("Error", true) || it.startsWith("Failed"))
            }
        }

        when (val current = step) {
            GrStep.List -> {
                liveList.error?.let { StatusBanner(it, isError = true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LiveSyncIndicator(liveList.lastUpdatedMs)
                    ErpPrimaryButton(
                        text = "New receipt",
                        onClick = {
                            message = null
                            step = GrStep.Create
                            createTab = 0
                            lineDrafts.clear()
                            sourceHeader = null
                            sourceType = "purchase_order"
                            selectedSource = null
                            selectedWarehouse = workspaceWarehouseId?.toLongOrNull()?.let { id ->
                                PickerOption(id, workspaceWarehouseName ?: "Warehouse", null)
                            }
                            selectedReceivedBy = null
                            receiptDate = LocalDate.now().toString()
                            deliveryNote = ""
                            vehicleNumber = ""
                            driverName = ""
                            receiptNotes = ""
                            createdPutawayTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth(0.48f),
                    )
                }
                JsonIndexListTable(
                    rows = liveList.rows,
                    columns = grIndexColumns,
                    sortSearch = listSortSearch,
                    emptyText = "No goods receipts found.",
                    searchPlaceholder = "Search receipts…",
                    loading = liveList.loading,
                    loadingMore = liveList.loadingMore,
                    hasMore = liveList.hasMore,
                    totalCount = liveList.totalCount,
                    onLoadMore = liveList.loadMore,
                    onRowClick = { gr ->
                        gr.long("id")?.let(::beginGrDetail)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            GrStep.Create -> {
                TabRow(createTab) {
                    Tab(selected = createTab == 0, onClick = { createTab = 0 }, text = { Text("Receipt info") })
                    Tab(
                        selected = createTab == 1,
                        onClick = { if (!linesLoading) createTab = 1 },
                        text = {
                            Text(
                                when {
                                    linesLoading -> "Items (…)"
                                    else -> "Items (${lineDrafts.size})"
                                },
                            )
                        },
                    )
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (createTab == 0) {
                        GrSourceSelectionSection(
                            sourceType = sourceType,
                            onSourceTypeChange = { type ->
                                sourceType = type
                                selectedSource = null
                                sourceHeader = null
                                lineDrafts.clear()
                            },
                            selectedSource = selectedSource,
                            sourceHeader = sourceHeader,
                            linesLoading = linesLoading,
                            lineCount = lineDrafts.size,
                            enabled = !linesLoading,
                            onOpenPicker = { sourcePickerOpen = true },
                            onClearSource = { selectedSource = null },
                        )
                        ErpCard {
                            GrSectionHeader(
                                icon = Icons.Default.Store,
                                title = "Receipt details",
                                subtitle = "Warehouse, date & delivery info",
                            )
                            Spacer(Modifier.height(12.dp))
                            SearchablePickerField(
                                label = "Warehouse",
                                selected = selectedWarehouse,
                                placeholder = "Choose warehouse",
                                onOpen = { whPickerOpen = true },
                                onClear = { selectedWarehouse = null },
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                receiptDate,
                                { receiptDate = it },
                                label = { Text("Receipt date") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                deliveryNote,
                                { deliveryNote = it },
                                label = { Text("Delivery note #") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                vehicleNumber,
                                { vehicleNumber = it },
                                label = { Text("Vehicle number") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                driverName,
                                { driverName = it },
                                label = { Text("Driver name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            SearchablePickerField(
                                label = "Received by",
                                selected = selectedReceivedBy,
                                placeholder = "Choose receiver",
                                onOpen = { userPickerOpen = true },
                                onClear = { selectedReceivedBy = null },
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                receiptNotes,
                                { receiptNotes = it },
                                label = { Text("Receipt notes") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        }
                    } else {
                        val createMatchLines = lineDrafts.mapIndexed { index, line ->
                            WorkflowMatchLine(
                                key = index.toString(),
                                productId = line.productId,
                                variationValueId = line.variationValueId,
                                rollNumber = line.rollNumber,
                                isRoll = line.isRollProduct,
                                label = buildString {
                                    append(line.productName)
                                    if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                    line.rollNumber?.let { append(" · roll ").append(it) }
                                },
                            )
                        }
                        WorkflowDataTable(
                            columns = listOf(
                                DataTableColumn("Product", 0.9f),
                                DataTableColumn("Var", 0.45f),
                                DataTableColumn("Roll", 0.35f),
                                DataTableColumn("Ord", 0.28f),
                                DataTableColumn("Prev", 0.28f),
                                DataTableColumn("Rem", 0.28f),
                                DataTableColumn("Acc", 0.28f),
                                DataTableColumn("Rej", 0.28f),
                            ),
                            rowBackground = { index ->
                                lineHighlights[createMatchLines.getOrNull(index)?.key]
                                    ?.let(scanColors::forStatus)
                                    ?: androidx.compose.ui.graphics.Color.Transparent
                            },
                            rows = lineDrafts.map { line ->
                                listOf(
                                    TableCell.Text(line.productName),
                                    TableCell.Text(line.variationLabel.ifBlank { "—" }),
                                    TableCell.Text(line.rollNumber ?: if (line.isRollProduct) "—" else ""),
                                    TableCell.Text(
                                        formatQtyWithUnit(
                                            DisplayFormat.qty(line.orderedQty),
                                            line.quantityUnitSuffix,
                                            line.isRollProduct,
                                        ),
                                    ),
                                    TableCell.Text(
                                        formatQtyWithUnit(
                                            DisplayFormat.qty(line.previouslyReceived),
                                            line.quantityUnitSuffix,
                                            line.isRollProduct,
                                        ),
                                    ),
                                    TableCell.Text(
                                        formatQtyWithUnit(
                                            DisplayFormat.qty(line.remainingQty),
                                            line.quantityUnitSuffix,
                                            line.isRollProduct,
                                        ),
                                    ),
                                    TableCell.Text(
                                        if (line.acceptedQty.isBlank()) "—"
                                        else formatQtyWithUnit(line.acceptedQty, line.quantityUnitSuffix, line.isRollProduct),
                                    ),
                                    TableCell.Text(
                                        if (line.rejectedQty.isBlank() || line.rejectedQty == "0") "—"
                                        else formatQtyWithUnit(line.rejectedQty, line.quantityUnitSuffix, line.isRollProduct),
                                    ),
                                )
                            },
                            emptyText = if (linesLoading) "Loading lines…" else "Choose a source on Receipt info to load lines.",
                            loading = linesLoading,
                        )
                        lineDrafts.forEachIndexed { index, line ->
                            val accLabel = if (line.isRollProduct) {
                                WorkflowJson.rollLengthLabel(line.quantityUnitSuffix, "Accepted length")
                            } else {
                                "Accepted"
                            }
                            val rejLabel = if (line.isRollProduct) {
                                WorkflowJson.rollLengthLabel(line.quantityUnitSuffix, "Rejected length")
                            } else {
                                "Rejected"
                            }
                            Text(
                                buildString {
                                    append(line.productName)
                                    if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                    line.rollNumber?.let { append(" · roll ").append(it) }
                                    line.rollLengthHint?.let { hint ->
                                        append(" · hint ")
                                        append(formatQtyWithUnit(DisplayFormat.qty(hint), line.quantityUnitSuffix, true))
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    line.acceptedQty,
                                    { v -> lineDrafts[index] = line.copy(acceptedQty = v) },
                                    label = { Text(accLabel) },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    line.rejectedQty,
                                    { v -> lineDrafts[index] = line.copy(rejectedQty = v) },
                                    label = { Text(rejLabel) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        WorkflowLineScanSection(
                            container = container,
                            tags = tags,
                            lines = createMatchLines,
                            onIncrementLine = { key, entry ->
                                val index = key.toIntOrNull() ?: return@WorkflowLineScanSection
                                val line = lineDrafts.getOrNull(index) ?: return@WorkflowLineScanSection
                                if (!shouldIncrementQtyOnScan(
                                        createMatchLines.getOrNull(index) ?: return@WorkflowLineScanSection,
                                        entry,
                                    )
                                ) {
                                    return@WorkflowLineScanSection
                                }
                                lineDrafts[index] = line.copy(
                                    acceptedQty = incrementQtyField(line.acceptedQty.ifBlank { "0" }, scanIncrementDelta(entry)),
                                )
                            },
                            onLineHighlightsChanged = { lineHighlights = it },
                            onClear = { container.rfidManager.clearScannedTags() },
                        )
                        ErpPrimaryButton(
                            text = "Save goods receipt",
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val sourceId = selectedSource?.id ?: error("Choose a source document")
                                    val whId = selectedWarehouse?.id ?: error("Choose a warehouse")
                                    val userId = selectedReceivedBy?.id ?: error("Choose who received this")
                                    val items = lineDrafts.map { line ->
                                        val acc = line.acceptedQty.toDoubleOrNull() ?: 0.0
                                        val rej = line.rejectedQty.toDoubleOrNull() ?: 0.0
                                        if (acc <= 0 && rej <= 0) {
                                            val label = buildString {
                                                append(line.productName)
                                                line.rollNumber?.let { append(" roll ").append(it) }
                                            }
                                            error(
                                                if (line.isRollProduct) {
                                                    "Enter measured length for $label"
                                                } else {
                                                    "Enter accepted or rejected qty for $label"
                                                },
                                            )
                                        }
                                        CreateGoodsReceiptItem(
                                            product_id = line.productId,
                                            product_name = line.productName,
                                            product_sku = line.productSku,
                                            ordered_quantity = line.orderedQty,
                                            received_quantity = acc + rej,
                                            accepted_quantity = acc,
                                            rejected_quantity = rej,
                                            purchase_order_item_id = if (sourceType == "purchase_order") line.sourceItemId else null,
                                            stock_transfer_item_id = if (sourceType == "stock_transfer") line.sourceItemId else null,
                                            sales_return_item_id = if (sourceType == "sales_return") line.sourceItemId else null,
                                            variation_value_id = line.variationValueId,
                                            quality_status = line.deriveQualityStatus(),
                                            roll_number = line.rollNumber,
                                            roll_length = line.acceptedQty.toDoubleOrNull()
                                                ?: line.rollLengthHint,
                                            packing_list_item_id = line.packingListItemId,
                                        )
                                    }
                                    val supplierId = when (sourceType) {
                                        "purchase_order" -> sourceHeader?.long("supplier_id")
                                        "sales_return" -> sourceHeader?.long("contact_id")
                                        else -> null
                                    }
                                    val body = CreateGoodsReceiptRequest(
                                        source_type = sourceType,
                                        source_id = sourceId,
                                        supplier_id = supplierId,
                                        warehouse_id = whId,
                                        receipt_date = receiptDate,
                                        received_by = userId,
                                        delivery_note_number = deliveryNote.ifBlank { null },
                                        vehicle_number = vehicleNumber.ifBlank { null },
                                        driver_name = driverName.ifBlank { null },
                                        notes = receiptNotes.ifBlank { null },
                                        total_items = items.size,
                                        total_received_quantity = items.sumOf { it.received_quantity ?: 0.0 },
                                        items = items,
                                    )
                                    val res = container.api.createGoodsReceipt(body)
                                    if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                                    val created = WorkflowJson.envelopeObject(res)
                                        ?: error("Created but empty response")
                                    val newId = created.long("id") ?: error("Created but no ID returned")
                                    val loaded = WorkflowJson.envelopeObject(container.api.getGoodsReceipt(newId)) ?: created
                                    applyGrDetail(loaded, newId)
                                    step = GrStep.Detail(newId)
                                    liveList.refresh()
                                    val putawayNum = createdPutawayTaskId?.let { taskId ->
                                        val tasks = created.array("putaway_tasks")
                                        tasks?.firstOrNull()?.asJsonObject?.string("putaway_task_number")
                                            ?: "#$taskId"
                                    }
                                    val putawayNote = putawayNum?.let { " · Putaway $it created" } ?: ""
                                    "Goods receipt saved$putawayNote"
                                }
                            },
                        )
                    }
                }
            }

            is GrStep.Detail -> {
                val grId = current.id
                if (detailLoading || detailLoadedId != grId) {
                    GoodsReceiptDetailSkeleton(Modifier.weight(1f))
                } else {
                val gr = detail
                val putawayTaskId = createdPutawayTaskId ?: gr?.firstPutawayTaskId()
                val receiptStatus = gr?.string("receipt_status") ?: gr?.string("status")
                val canEditLines = receiptStatus in setOf("pending", "partial", "draft")

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gr?.let {
                        Text(
                            "Source: ${DisplayFormat.status(it.string("source_type"))} #${it.long("source_id")} · " +
                                DisplayFormat.status(it.string("receipt_status") ?: it.string("status") ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (putawayTaskId != null) {
                        val putawayLabel = gr?.array("putaway_tasks")?.firstOrNull()?.asJsonObject
                            ?.string("putaway_task_number") ?: "Putaway #$putawayTaskId"
                        ErpPrimaryButton(
                            text = "Open $putawayLabel",
                            onClick = { onOpenPutaway(putawayTaskId) },
                        )
                    } else {
                        Text(
                            "No putaway task yet — only approved accepted qty creates putaway.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (canEditLines) {
                        TabRow(detailEditTab) {
                            Tab(selected = detailEditTab == 0, onClick = { detailEditTab = 0 }, text = { Text("Receipt info") })
                            Tab(
                                selected = detailEditTab == 1,
                                onClick = { detailEditTab = 1 },
                                text = { Text("Items (${detailLineEdits.size})") },
                            )
                        }
                    }
                    if (canEditLines && detailEditTab == 0) {
                        ErpCard {
                            GrSectionHeader(
                                icon = Icons.Default.Store,
                                title = "Receipt details",
                                subtitle = "Edit delivery info before putaway completes",
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Warehouse: ${gr?.obj("warehouse")?.string("name") ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                detailHeaderDate,
                                { detailHeaderDate = it },
                                label = { Text("Receipt date") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                detailHeaderDeliveryNote,
                                { detailHeaderDeliveryNote = it },
                                label = { Text("Delivery note #") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                detailHeaderVehicle,
                                { detailHeaderVehicle = it },
                                label = { Text("Vehicle number") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                detailHeaderDriver,
                                { detailHeaderDriver = it },
                                label = { Text("Driver name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            SearchablePickerField(
                                label = "Received by",
                                selected = detailHeaderReceivedBy,
                                placeholder = "Choose receiver",
                                onOpen = { userPickerOpen = true },
                                onClear = { detailHeaderReceivedBy = null },
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                detailHeaderNotes,
                                { detailHeaderNotes = it },
                                label = { Text("Receipt notes") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        }
                        ErpPrimaryButton(
                            text = "Save changes",
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val result = saveGoodsReceiptDetail(
                                        container = container,
                                        grId = grId,
                                        gr = detail ?: error("Receipt not loaded"),
                                        lineEdits = detailLineEdits.toList(),
                                        header = GrDetailHeaderEdit(
                                            receiptDate = detailHeaderDate,
                                            deliveryNote = detailHeaderDeliveryNote,
                                            vehicleNumber = detailHeaderVehicle,
                                            driverName = detailHeaderDriver,
                                            receivedById = detailHeaderReceivedBy?.id
                                                ?: error("Choose who received this"),
                                            notes = detailHeaderNotes,
                                        ),
                                    )
                                    applyGrSaveToUi(
                                        result,
                                        setDetail = { detail = it },
                                        setPutawayTaskId = { createdPutawayTaskId = it },
                                        setHeaderDate = { detailHeaderDate = it },
                                        setHeaderDeliveryNote = { detailHeaderDeliveryNote = it },
                                        setHeaderVehicle = { detailHeaderVehicle = it },
                                        setHeaderDriver = { detailHeaderDriver = it },
                                        setHeaderNotes = { detailHeaderNotes = it },
                                        setHeaderReceivedBy = { detailHeaderReceivedBy = it },
                                        reloadLineEdits = {
                                            detailLineEdits.clear()
                                            detail?.let { nestedItems(it).mapNotNull(::detailItemToEdit) }
                                                ?.forEach { edit -> detailLineEdits.add(edit) }
                                        },
                                    )
                                    result.message
                                }
                            },
                        )
                    } else if (!canEditLines) {
                        gr?.let {
                            Text(
                                buildString {
                                    append("Date: ${it.string("receipt_date") ?: "—"}")
                                    append(" · WH: ${it.obj("warehouse")?.string("name") ?: "—"}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            it.string("delivery_note_number")?.takeIf { n -> n.isNotBlank() }?.let { note ->
                                Text("Delivery note: $note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val vehicle = it.string("vehicle_number")
                            val driver = it.string("driver_name")
                            if (!vehicle.isNullOrBlank() || !driver.isNullOrBlank()) {
                                Text(
                                    listOfNotNull(
                                        vehicle?.takeIf { v -> v.isNotBlank() }?.let { v -> "Vehicle: $v" },
                                        driver?.takeIf { d -> d.isNotBlank() }?.let { d -> "Driver: $d" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val receiver = it.obj("received_by_user")?.string("name")
                                ?: it.long("received_by")?.let { id -> "User #$id" }
                            Text("Received by: $receiver", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            it.string("notes")?.takeIf { n -> n.isNotBlank() }?.let { note ->
                                Text("Notes: $note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (!canEditLines || detailEditTab == 1) {
                    val detailMatchLines = detailLineEdits.map { line ->
                        WorkflowMatchLine(
                            key = line.goodsReceiptItemId.toString(),
                            productId = line.productId,
                            variationValueId = line.variationValueId,
                            rollNumber = line.rollNumber,
                            isRoll = line.isRollProduct,
                            label = buildString {
                                append(line.productName)
                                if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                line.rollNumber?.let { append(" · roll ").append(it) }
                            },
                        )
                    }
                    WorkflowDataTable(
                        columns = listOf(
                            DataTableColumn("Product", 0.9f),
                            DataTableColumn("Var", 0.5f),
                            DataTableColumn("Roll", 0.4f),
                            DataTableColumn("Ord", 0.32f),
                            DataTableColumn("Acc", 0.32f),
                            DataTableColumn("Rej", 0.32f),
                        ),
                        rowBackground = { index ->
                            detailMatchLines.getOrNull(index)?.key?.let { detailLineHighlights[it] }
                                ?.let(scanColors::forStatus)
                                ?: androidx.compose.ui.graphics.Color.Transparent
                        },
                        rows = detailLineEdits.map { line ->
                            listOf(
                                TableCell.Text(line.productName),
                                TableCell.Text(line.variationLabel.ifBlank { "—" }),
                                TableCell.Text(line.rollNumber ?: if (line.isRollProduct) "—" else ""),
                                TableCell.Text(
                                    formatQtyWithUnit(
                                        DisplayFormat.qty(line.orderedQty),
                                        line.quantityUnitSuffix,
                                        line.isRollProduct,
                                    ),
                                ),
                                TableCell.Text(
                                    if (line.acceptedQty.isBlank()) "—"
                                    else formatQtyWithUnit(line.acceptedQty, line.quantityUnitSuffix, line.isRollProduct),
                                ),
                                TableCell.Text(
                                    if (line.rejectedQty.isBlank() || line.rejectedQty == "0") "—"
                                    else formatQtyWithUnit(line.rejectedQty, line.quantityUnitSuffix, line.isRollProduct),
                                ),
                            )
                        },
                        emptyText = "No line items.",
                        modifier = Modifier.weight(1f),
                    )
                    if (canEditLines) {
                        detailLineEdits.forEachIndexed { index, line ->
                            val accLabel = if (line.isRollProduct) {
                                rollLengthLabel(line.quantityUnitSuffix, "Accepted length")
                            } else {
                                "Accepted"
                            }
                            val rejLabel = if (line.isRollProduct) {
                                rollLengthLabel(line.quantityUnitSuffix, "Rejected length")
                            } else {
                                "Rejected"
                            }
                            Text(
                                buildString {
                                    append(line.productName)
                                    if (line.variationLabel.isNotBlank()) append(" · ").append(line.variationLabel)
                                    line.rollNumber?.let { append(" · roll ").append(it) }
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    line.acceptedQty,
                                    { v -> detailLineEdits[index] = line.copy(acceptedQty = v) },
                                    label = { Text(accLabel) },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    line.rejectedQty,
                                    { v -> detailLineEdits[index] = line.copy(rejectedQty = v) },
                                    label = { Text(rejLabel) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        Text(
                            "Receipt is locked — quantities cannot be edited.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WorkflowLineScanSection(
                        container = container,
                        tags = tags,
                        lines = detailMatchLines,
                        onIncrementLine = if (canEditLines) {
                            { key, entry ->
                                val index = detailLineEdits.indexOfFirst { it.goodsReceiptItemId.toString() == key }
                                if (index >= 0) {
                                    val line = detailLineEdits[index]
                                    val matchLine = detailMatchLines.getOrNull(index)
                                    if (matchLine != null && shouldIncrementQtyOnScan(matchLine, entry)) {
                                        detailLineEdits[index] = line.copy(
                                            acceptedQty = incrementQtyField(
                                                line.acceptedQty.ifBlank { "0" },
                                                scanIncrementDelta(entry),
                                            ),
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onLineHighlightsChanged = { detailLineHighlights = it },
                        onClear = { container.rfidManager.clearScannedTags() },
                    )
                    if (canEditLines) {
                        ErpPrimaryButton(
                            text = "Save changes",
                            loading = actionLoading,
                            onClick = {
                                scope.launchWorkflow(
                                    setLoading = { actionLoading = it },
                                    onError = { message = it },
                                    onSuccess = { message = it },
                                ) {
                                    val result = saveGoodsReceiptDetail(
                                        container = container,
                                        grId = grId,
                                        gr = detail ?: error("Receipt not loaded"),
                                        lineEdits = detailLineEdits.toList(),
                                        header = GrDetailHeaderEdit(
                                            receiptDate = detailHeaderDate,
                                            deliveryNote = detailHeaderDeliveryNote,
                                            vehicleNumber = detailHeaderVehicle,
                                            driverName = detailHeaderDriver,
                                            receivedById = detailHeaderReceivedBy?.id
                                                ?: error("Choose who received this"),
                                            notes = detailHeaderNotes,
                                        ),
                                    )
                                    applyGrSaveToUi(
                                        result,
                                        setDetail = { detail = it },
                                        setPutawayTaskId = { createdPutawayTaskId = it },
                                        setHeaderDate = { detailHeaderDate = it },
                                        setHeaderDeliveryNote = { detailHeaderDeliveryNote = it },
                                        setHeaderVehicle = { detailHeaderVehicle = it },
                                        setHeaderDriver = { detailHeaderDriver = it },
                                        setHeaderNotes = { detailHeaderNotes = it },
                                        setHeaderReceivedBy = { detailHeaderReceivedBy = it },
                                        reloadLineEdits = {
                                            detailLineEdits.clear()
                                            detail?.let { nestedItems(it).mapNotNull(::detailItemToEdit) }
                                                ?.forEach { edit -> detailLineEdits.add(edit) }
                                        },
                                    )
                                    result.message
                                }
                            },
                        )
                    }
                    ErpPrimaryButton(text = "Sync scans to server", onClick = {
                        scope.launchWorkflow(
                            onError = { message = it },
                            onSuccess = { message = it },
                        ) {
                            val epcs = tags.map { it.epc }
                            if (epcs.isEmpty()) error("Scan tags first")
                            val res = container.api.goodsReceiptRfidScan(
                                grId,
                                WorkflowScanRequest(epcs, UUID.randomUUID().toString()),
                            )
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            queueReads(container, "receiving", epcs)
                            detail = WorkflowJson.envelopeObject(container.api.getGoodsReceipt(grId))
                            res.body()?.message ?: "Matched"
                        }
                    })
                    }
                }
                }
            }
        }
    }

    SearchablePickerSheet(
        visible = sourcePickerOpen,
        title = when (sourceType) {
            "stock_transfer" -> "Stock transfer"
            "sales_return" -> "Sales return"
            else -> "Purchase order"
        },
        options = sourceOptions,
        loading = pickerLoading,
        onDismiss = { sourcePickerOpen = false },
        onSelect = { selectedSource = it },
        onSearch = { sourceSearchQuery = it },
        searchHint = "Search source…",
    )
    SearchablePickerSheet(
        visible = whPickerOpen,
        title = "Warehouse",
        options = whOptions,
        loading = pickerLoading,
        onDismiss = { whPickerOpen = false },
        onSelect = { selectedWarehouse = it },
        onSearch = { whSearchQuery = it },
        searchHint = "Search warehouse…",
    )
    SearchablePickerSheet(
        visible = userPickerOpen,
        title = "Received by",
        options = userOptions,
        loading = pickerLoading,
        onDismiss = { userPickerOpen = false },
        onSelect = { option ->
            if (step is GrStep.Detail) {
                detailHeaderReceivedBy = option
            } else {
                selectedReceivedBy = option
            }
        },
        onSearch = { userSearchQuery = it },
        searchHint = "Search user…",
    )
}

private data class GrSourceTypeChoice(
    val type: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val pickerPlaceholder: String,
)

private val GR_SOURCE_TYPES = listOf(
    GrSourceTypeChoice(
        type = "purchase_order",
        title = "Purchase order",
        description = "Receive stock from a supplier PO",
        icon = Icons.Default.ShoppingCart,
        pickerPlaceholder = "Search open purchase orders",
    ),
    GrSourceTypeChoice(
        type = "stock_transfer",
        title = "Stock transfer",
        description = "Inbound from another warehouse",
        icon = Icons.Default.SwapHoriz,
        pickerPlaceholder = "Search transfers ready to receive",
    ),
    GrSourceTypeChoice(
        type = "sales_return",
        title = "Sales return",
        description = "Customer return documents",
        icon = Icons.AutoMirrored.Filled.Reply,
        pickerPlaceholder = "Search sales returns",
    ),
)

private fun grSourceTypeChoice(type: String): GrSourceTypeChoice =
    GR_SOURCE_TYPES.firstOrNull { it.type == type } ?: GR_SOURCE_TYPES.first()

private fun grSourceHeaderSummary(sourceType: String, header: JsonObject?): String? {
    if (header == null) return null
    return when (sourceType) {
        "stock_transfer" -> {
            val from = header.obj("from_warehouse")?.string("name")
                ?: header.obj("fromWarehouse")?.string("name")
            val to = header.obj("to_warehouse")?.string("name")
                ?: header.obj("toWarehouse")?.string("name")
            listOfNotNull(from, to).joinToString(" → ").takeIf { it.isNotBlank() }
        }
        "sales_return" -> header.obj("contact")?.string("company")
            ?: header.obj("contact")?.string("contact_person")
        else -> header.obj("supplier")?.string("company")
            ?: header.obj("supplier")?.string("contact_person")
    }
}

@Composable
private fun GrSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GrSourceTypeOption(
    choice: GrSourceTypeChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                choice.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                choice.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                choice.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GrDocumentPickerCard(
    choice: GrSourceTypeChoice,
    selected: PickerOption?,
    headerSummary: String?,
    linesLoading: Boolean,
    lineCount: Int,
    enabled: Boolean,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected != null) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val bg = if (selected != null) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled) { onOpen() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected != null) Icons.Default.Description else choice.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    selected?.title ?: choice.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    linesLoading -> "Loading line items…"
                    selected != null -> listOfNotNull(
                        selected.subtitle?.takeIf { it.isNotBlank() },
                        headerSummary?.takeIf { it.isNotBlank() && it != selected.subtitle },
                    ).joinToString(" · ").ifBlank { choice.pickerPlaceholder }
                    else -> choice.pickerPlaceholder
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                linesLoading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                selected != null -> IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear source")
                }
                else -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected != null && !linesLoading && lineCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    "$lineCount line${if (lineCount == 1) "" else "s"} ready — open Items tab to receive",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun GrSourceSelectionSection(
    sourceType: String,
    onSourceTypeChange: (String) -> Unit,
    selectedSource: PickerOption?,
    sourceHeader: JsonObject?,
    linesLoading: Boolean,
    lineCount: Int,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onClearSource: () -> Unit,
) {
    val choice = grSourceTypeChoice(sourceType)
    val headerSummary = grSourceHeaderSummary(sourceType, sourceHeader)

    ErpCard {
        GrSectionHeader(
            icon = Icons.Default.Description,
            title = "Receive from",
            subtitle = "Choose source type and document",
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GR_SOURCE_TYPES.forEach { typeChoice ->
                GrSourceTypeOption(
                    choice = typeChoice,
                    selected = sourceType == typeChoice.type,
                    onClick = { onSourceTypeChange(typeChoice.type) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Spacer(Modifier.height(14.dp))
        Text(
            "Source document",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        GrDocumentPickerCard(
            choice = choice,
            selected = selectedSource,
            headerSummary = headerSummary,
            linesLoading = linesLoading,
            lineCount = lineCount,
            enabled = enabled,
            onOpen = onOpenPicker,
            onClear = onClearSource,
        )
    }
}
