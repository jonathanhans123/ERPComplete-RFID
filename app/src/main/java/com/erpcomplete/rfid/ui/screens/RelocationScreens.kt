package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.CreateStockRelocationRequest
import com.erpcomplete.rfid.data.remote.RelocationProductPayload
import com.erpcomplete.rfid.data.remote.RelocationRollDataPayload
import com.erpcomplete.rfid.data.remote.RelocationRollLinePayload
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
import com.erpcomplete.rfid.ui.components.SortableCardListToolbar
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.WorkflowListCardSkeleton
import com.erpcomplete.rfid.ui.components.applyCardListSortSearch
import com.erpcomplete.rfid.ui.components.rememberTableSortSearch
import com.erpcomplete.rfid.ui.components.rememberWorkflowLiveList
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.PickerMappers
import com.erpcomplete.rfid.util.StockLinePreset
import com.erpcomplete.rfid.util.UNMARKED_STOCK_LOCATION_ID
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.envelopeList
import com.erpcomplete.rfid.util.WorkflowJson.envelopeObject
import com.erpcomplete.rfid.util.WorkflowJson.envelopePage
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.nestedItems
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.rollLengthLabel
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.workspaceContext
import com.google.gson.JsonObject
import java.time.LocalDate

internal data class RelocationFormPreset(
    val productId: Long? = null,
    val productLabel: String? = null,
    val variationValueId: Long? = null,
    val variationLabel: String? = null,
    val fromLocationId: Long? = null,
    val moveQty: Double? = null,
    val batchNumber: String? = null,
    val rollNumber: String? = null,
    val isRoll: Boolean = false,
    val quantityUnitSuffix: String? = null,
)

@Composable
internal fun RelocationListScreen(
    container: AppContainer,
    canCreate: Boolean = true,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val whId = container.workspaceContext().warehouseId
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        val res = container.api.listStockRelocations(warehouseId = whId, page = page, perPage = 50)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Number", "Status", "Date", "Reason")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { it.string("stock_relocation_number") ?: "" },
                { it.string("status") ?: "" },
                { it.string("relocation_date") ?: "" },
                { it.string("reason") ?: "" },
            ),
        )
    }

    ErpScaffold(
        title = "Relocations",
        subtitle = "Move stock between bins",
        onBack = onBack,
        actions = {
            if (canCreate) {
                IconButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = "New relocation")
                }
            }
        },
    ) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search relocations…",
        )
        when {
            liveList.loading && visibleRows.isEmpty() -> WorkflowListCardSkeleton(5)
            visibleRows.isEmpty() -> Text("No relocations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> visibleRows.forEach { row ->
                val id = row.long("id") ?: return@forEach
                ErpCard(onClick = { onOpen(id) }) {
                    Text(row.string("stock_relocation_number") ?: "—", fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            DisplayFormat.status(row.string("status")),
                            row.string("relocation_date"),
                            row.string("reason"),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RelocationFormScreen(
    container: AppContainer,
    preset: RelocationFormPreset,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val whId = container.workspaceContext().warehouseId
    if (whId == null) {
        ErpScaffold(title = "Relocate stock", onBack = onBack) {
            StatusBanner("Select a workspace warehouse first.", isError = true)
        }
        return
    }

    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedProduct by remember { mutableStateOf<PickerOption?>(null) }
    var selectedVariation by remember { mutableStateOf<PickerOption?>(null) }
    var variationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var fromLocation by remember { mutableStateOf<PickerOption?>(null) }
    var toLocation by remember { mutableStateOf<PickerOption?>(null) }
    var locationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var reason by remember { mutableStateOf("Mobile bin relocation") }
    var notes by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf(preset.batchNumber ?: "") }
    var rollNumber by remember { mutableStateOf(preset.rollNumber ?: "") }
    var isRollProduct by remember { mutableStateOf(preset.isRoll) }
    var quantityUnitSuffix by remember { mutableStateOf(preset.quantityUnitSuffix) }
    var sourceQty by remember { mutableStateOf(preset.moveQty?.let { DisplayFormat.qty(it) } ?: "") }
    var moveQty by remember { mutableStateOf(preset.moveQty?.let { DisplayFormat.qty(it) } ?: "") }

    var productPickerOpen by remember { mutableStateOf(false) }
    var variationPickerOpen by remember { mutableStateOf(false) }
    var fromLocationPickerOpen by remember { mutableStateOf(false) }
    var toLocationPickerOpen by remember { mutableStateOf(false) }
    var productOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }

    val requiresVariation = variationOptions.isNotEmpty()
    val qtyLabel = if (isRollProduct) rollLengthLabel(quantityUnitSuffix) else "Available at source"
    val moveLabel = if (isRollProduct) {
        val suffix = quantityUnitSuffix?.takeIf { it.isNotBlank() }
        if (suffix != null) "Length to move ($suffix)" else "Length to move"
    } else {
        "Quantity to move"
    }

    suspend fun fetchSourceStock(productId: Long) {
        val fromId = fromLocation?.id?.takeUnless { it == UNMARKED_STOCK_LOCATION_ID }
        val res = container.api.getRelocationProductStock(
            productId = productId,
            warehouseId = whId,
            fromWarehouseLocationId = fromId,
            variationId = selectedVariation?.id,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        val body = res.body()?.asJsonObject
        if (body?.get("requires_variation")?.asBoolean == true) {
            sourceQty = ""
            message = "Select a variation to load source stock."
            return
        }
        isRollProduct = body?.get("is_roll")?.asBoolean == true || isRollProduct
        quantityUnitSuffix = body?.get("quantity_unit_suffix")?.asString ?: quantityUnitSuffix
        val qty = body?.get("from_warehouse_stock_quantity")?.asDouble ?: 0.0
        sourceQty = DisplayFormat.qty(qty)
        if (moveQty.isBlank() && qty > 0) moveQty = DisplayFormat.qty(qty)
        message = null
    }

    fun loadVariations(productId: Long, preselectVariationId: Long? = null) {
        scope.launchWorkflow({ loading = it }, { message = it }) {
            val res = container.api.getProductVariations(productId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            val body = res.body()?.asJsonObject
            isRollProduct = body?.get("product_type")?.asString == "roll"
            val options = parseRelocationVariationOptions(body?.getAsJsonArray("variations"))
            variationOptions = options
            selectedVariation = preselectVariationId?.let { id -> options.firstOrNull { it.id == id } }
            if (options.isEmpty() || selectedVariation != null) {
                fetchSourceStock(productId)
            } else {
                sourceQty = ""
            }
            null
        }
    }

    fun refreshSourceStock() {
        val productId = selectedProduct?.id ?: return
        if (variationOptions.isNotEmpty() && selectedVariation == null) {
            sourceQty = ""
            message = "Select a variation to load source stock."
            return
        }
        scope.launchWorkflow({ loading = it }, { message = it }) {
            fetchSourceStock(productId)
            null
        }
    }

    LaunchedEffect(preset.productId) {
        val unmarked = PickerOption(UNMARKED_STOCK_LOCATION_ID, "Unmarked location")
        locationOptions = listOf(unmarked)
        runCatching {
            val locRes = container.api.listWarehouseLocations(warehouseId = whId, perPage = 200)
            if (locRes.isSuccessful) {
                locationOptions = listOf(unmarked) + envelopeList(locRes).mapNotNull(PickerMappers::warehouseLocation)
            }
        }
        preset.fromLocationId?.let { locId ->
            fromLocation = locationOptions.firstOrNull { it.id == locId } ?: unmarked
        }
        if (preset.productId != null) {
            selectedProduct = PickerOption(
                preset.productId,
                preset.productLabel ?: "Product #${preset.productId}",
            )
            preset.variationLabel?.let { label ->
                preset.variationValueId?.let { id ->
                    selectedVariation = PickerOption(id, label)
                }
            }
            loadVariations(preset.productId, preset.variationValueId)
        }
    }

    ErpScaffold(title = "Relocate stock", subtitle = "Same warehouse · different bin", onBack = onBack) {
        message?.let { StatusBanner(it, isError = true) }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchablePickerField(
                label = "From location",
                selected = fromLocation,
                placeholder = "Source bin (or unmarked)",
                onOpen = { fromLocationPickerOpen = true },
                onClear = {
                    fromLocation = null
                    refreshSourceStock()
                },
            )
            SearchablePickerField(
                label = "To location",
                selected = toLocation,
                placeholder = "Destination bin",
                onOpen = { toLocationPickerOpen = true },
                onClear = { toLocation = null },
            )
            SearchablePickerField(
                label = "Product",
                selected = selectedProduct,
                placeholder = "Choose product",
                onOpen = { productPickerOpen = true },
            )
            if (selectedProduct != null && requiresVariation) {
                SearchablePickerField(
                    label = "Variation",
                    selected = selectedVariation,
                    placeholder = "Choose variation",
                    onOpen = { variationPickerOpen = true },
                    onClear = {
                        selectedVariation = null
                        sourceQty = ""
                    },
                )
            }
            if (isRollProduct) {
                OutlinedTextField(
                    rollNumber,
                    { rollNumber = it },
                    label = { Text("Roll number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                OutlinedTextField(
                    batchNumber,
                    { batchNumber = it },
                    label = { Text("Batch (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                sourceQty,
                { sourceQty = it },
                label = { Text(qtyLabel) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { refreshSourceStock() }) { Text("Refresh source stock") }
            }
            OutlinedTextField(moveQty, { moveQty = it }, label = { Text(moveLabel) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            ErpPrimaryButton(
                text = "Submit relocation",
                loading = loading,
                enabled = selectedProduct != null && moveQty.isNotBlank() && toLocation != null,
                onClick = {
                    val productId = selectedProduct?.id ?: return@ErpPrimaryButton
                    if (requiresVariation && selectedVariation == null) {
                        message = "Select a variation for this product."
                        return@ErpPrimaryButton
                    }
                    if (isRollProduct && rollNumber.isBlank()) {
                        message = "Enter the roll number to relocate."
                        return@ErpPrimaryButton
                    }
                    val qty = moveQty.toDoubleOrNull() ?: return@ErpPrimaryButton
                    if (qty <= 0) {
                        message = "Enter a quantity greater than zero."
                        return@ErpPrimaryButton
                    }
                    val fromId = fromLocation?.id
                    val toId = toLocation?.id
                    if (fromId != null && toId != null && fromId == toId) {
                        message = "From and to locations must be different."
                        return@ErpPrimaryButton
                    }
                    if (fromId == null && toId == UNMARKED_STOCK_LOCATION_ID) {
                        message = "Choose a destination bin different from unmarked source."
                        return@ErpPrimaryButton
                    }
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val payload = buildRelocationProductPayload(
                            productId = productId,
                            isRoll = isRollProduct,
                            quantity = qty,
                            variationId = selectedVariation?.id,
                            rollNumber = rollNumber.ifBlank { null },
                        )
                        val body = CreateStockRelocationRequest(
                            warehouse_id = whId,
                            from_warehouse_location_id = fromId?.takeUnless { it == UNMARKED_STOCK_LOCATION_ID },
                            to_warehouse_location_id = toId?.takeUnless { it == UNMARKED_STOCK_LOCATION_ID },
                            relocation_date = LocalDate.now().toString(),
                            reason = reason.trim(),
                            notes = notes.ifBlank { null },
                            products = listOf(payload),
                        )
                        val res = container.api.createStockRelocation(body)
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        val created = envelopeObject(res)
                        val status = created?.string("status")
                        if (status == "pending") {
                            val createdId = created.long("id")
                            if (createdId != null) {
                                val approveRes = container.api.approveStockRelocation(createdId)
                                if (!approveRes.isSuccessful) {
                                    message = "Created pending approval: ${ApiErrorParser.httpMessage(approveRes)}"
                                    onSaved()
                                    return@launchWorkflow null
                                }
                            }
                        }
                        onSaved()
                        null
                    }
                },
            )
        }
    }

    if (productPickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "Product",
            options = productOptions,
            onDismiss = { productPickerOpen = false },
            onSearch = { query ->
                scope.launchWorkflow(setLoading = {}, onError = {}) {
                    val res = container.api.listProducts(search = query.ifBlank { null }, perPage = 50)
                    if (res.isSuccessful) productOptions = envelopeList(res).mapNotNull(PickerMappers::product)
                    null
                }
            },
            onSelect = {
                selectedProduct = it
                productPickerOpen = false
                selectedVariation = null
                variationOptions = emptyList()
                rollNumber = ""
                batchNumber = ""
                sourceQty = ""
                moveQty = ""
                loadVariations(it.id)
            },
        )
    }
    if (variationPickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "Variation",
            options = variationOptions,
            onDismiss = { variationPickerOpen = false },
            onSelect = {
                selectedVariation = it
                variationPickerOpen = false
                refreshSourceStock()
            },
            searchHint = "Filter variations…",
        )
    }
    if (fromLocationPickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "From location",
            options = locationOptions,
            onDismiss = { fromLocationPickerOpen = false },
            onSelect = {
                fromLocation = it
                fromLocationPickerOpen = false
                refreshSourceStock()
            },
        )
    }
    if (toLocationPickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "To location",
            options = locationOptions,
            onDismiss = { toLocationPickerOpen = false },
            onSelect = {
                toLocation = it
                toLocationPickerOpen = false
            },
        )
    }
}

@Composable
internal fun RelocationDetailScreen(
    container: AppContainer,
    relocationId: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var actionLoading by remember { mutableStateOf(false) }
    var detailLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var relocation by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(relocationId) {
        relocation = null
        detailLoading = true
        runCatching {
            val res = container.api.getStockRelocation(relocationId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            relocation = envelopeObject(res)
        }.onFailure { message = it.message }
        detailLoading = false
    }

    val status = relocation?.string("status")
    val number = relocation?.string("stock_relocation_number") ?: "Relocation"
    val items = relocation?.let { nestedItems(it, "items") } ?: emptyList()

    ErpScaffold(title = number, subtitle = DisplayFormat.status(status), onBack = onBack) {
        message?.let { StatusBanner(it, isError = it.contains("Error", true) || it.contains("Failed", true)) }
        if (detailLoading) {
            WorkflowListCardSkeleton(3)
        } else {
            relocation?.let { doc ->
                ErpCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Reason: ${doc.string("reason") ?: "—"}")
                        Text("Date: ${doc.string("relocation_date") ?: "—"}")
                        doc.obj("from_location")?.let {
                            Text("From: ${WorkflowJson.locationLabel(it)}", style = MaterialTheme.typography.bodySmall)
                        } ?: Text("From: Unmarked location", style = MaterialTheme.typography.bodySmall)
                        doc.obj("to_location")?.let {
                            Text("To: ${WorkflowJson.locationLabel(it)}", style = MaterialTheme.typography.bodySmall)
                        } ?: Text("To: Unmarked location", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items.forEach { line ->
                val isRollLine = line.isRollStockLine()
                ErpCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.productName(), fontWeight = FontWeight.SemiBold)
                        line.variationLabel().takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        line.string("roll_number")?.takeIf { it.isNotBlank() }?.let {
                            Text("Roll $it", style = MaterialTheme.typography.bodySmall)
                        }
                        val qty = line.double("quantity") ?: 0.0
                        val unit = line.quantityUnitSuffix()
                        Text(
                            if (isRollLine) {
                                "Length: ${formatQtyWithUnit(DisplayFormat.qty(qty), unit, isRoll = true)}"
                            } else {
                                "Qty: ${DisplayFormat.qty(qty)}"
                            },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (status == "pending") {
                ErpPrimaryButton(
                    text = "Approve & move stock",
                    loading = actionLoading,
                    onClick = {
                        scope.launchWorkflow({ actionLoading = it }, { message = it }) {
                            val res = container.api.approveStockRelocation(relocationId)
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            onChanged()
                            null
                        }
                    },
                )
            }
        }
    }
}

internal fun stockLinePresetToRelocationForm(preset: StockLinePreset): RelocationFormPreset =
    RelocationFormPreset(
        productId = preset.productId,
        productLabel = preset.productLabel,
        variationValueId = preset.variationValueId,
        variationLabel = preset.variationLabel,
        fromLocationId = preset.locationId,
        moveQty = preset.currentQty,
        batchNumber = preset.batchNumber,
        rollNumber = preset.rollNumber,
        isRoll = preset.isRoll,
        quantityUnitSuffix = preset.quantityUnitSuffix,
    )

private fun buildRelocationProductPayload(
    productId: Long,
    isRoll: Boolean,
    quantity: Double,
    variationId: Long?,
    rollNumber: String?,
): RelocationProductPayload {
    return if (isRoll) {
        RelocationProductPayload(
            id = productId,
            type = "roll",
            quantity = quantity,
            variation_value_id = variationId,
            roll_data = RelocationRollDataPayload(
                rolls = listOf(
                    RelocationRollLinePayload(
                        roll_number = rollNumber,
                        used_length = quantity,
                    ),
                ),
            ),
        )
    } else {
        RelocationProductPayload(
            id = productId,
            type = "simple",
            quantity = quantity,
            variation_value_id = variationId,
        )
    }
}

private fun parseRelocationVariationOptions(variations: com.google.gson.JsonArray?): List<PickerOption> =
    variations?.mapNotNull { el ->
        val v = el.asJsonObject
        val id = v.long("id") ?: return@mapNotNull null
        val attrs = v.getAsJsonArray("descriptorValues") ?: v.getAsJsonArray("attributes")
        val attrText = attrs?.mapNotNull { a ->
            val ao = a.asJsonObject
            val name = ao.string("name") ?: ao.obj("variationDescriptor")?.string("name")
            val value = ao.string("value")
            if (!name.isNullOrBlank() && !value.isNullOrBlank()) "$name: $value" else null
        }?.joinToString(" · ")
        val main = v.string("value") ?: v.string("display_label")
        val title = listOfNotNull(main, attrText).joinToString(" — ").ifBlank { "Variation #$id" }
        PickerOption(id, title, attrText)
    } ?: emptyList()
