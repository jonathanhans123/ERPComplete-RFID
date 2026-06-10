package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.CreateStockAdjustmentRequest
import com.erpcomplete.rfid.data.remote.WarehouseLocationUpsertRequest
import com.erpcomplete.rfid.data.remote.WarehouseUpsertRequest
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.SortableCardListToolbar
import com.erpcomplete.rfid.ui.components.applyCardListSortSearch
import com.erpcomplete.rfid.ui.components.rememberTableSortSearch
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.WorkflowTile
import com.erpcomplete.rfid.ui.components.rememberWorkflowLiveList
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.DisplayFormat
import com.erpcomplete.rfid.util.PickerMappers
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.boolean
import com.erpcomplete.rfid.util.WorkflowJson.double
import com.erpcomplete.rfid.util.WorkflowJson.envelopeList
import com.erpcomplete.rfid.util.WorkflowJson.envelopePage
import com.erpcomplete.rfid.util.WorkflowJson.envelopeObject
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.nestedItems
import com.erpcomplete.rfid.util.WorkflowJson.obj
import com.erpcomplete.rfid.util.WorkflowJson.productName
import com.erpcomplete.rfid.util.WorkflowJson.productSku
import com.erpcomplete.rfid.util.WorkflowJson.quantityUnitSuffix
import com.erpcomplete.rfid.util.WorkflowJson.rollLengthLabel
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.erpcomplete.rfid.util.WorkflowJson.variationLabel
import com.google.gson.JsonArray
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.workspaceContext
import com.google.gson.JsonObject
import java.time.LocalDate

private sealed class InvStep {
    data object Hub : InvStep()
    data object Warehouses : InvStep()
    data class WarehouseForm(val id: Long?) : InvStep()
    data object Locations : InvStep()
    data class LocationForm(val id: Long?) : InvStep()
    data object StockByLocation : InvStep()
    data class LocationStock(val locationId: Long) : InvStep()
    data object Adjustments : InvStep()
    data class AdjustmentForm(
        val productId: Long? = null,
        val productLabel: String? = null,
        val variationValueId: Long? = null,
        val variationLabel: String? = null,
        val locationId: Long? = null,
        val currentQty: Double? = null,
        val batchNumber: String? = null,
        val rollNumber: String? = null,
        val isRoll: Boolean = false,
        val quantityUnitSuffix: String? = null,
    ) : InvStep()
    data class AdjustmentDetail(val id: Long) : InvStep()
}

private data class HubTile(
    val title: String,
    val desc: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val step: InvStep,
)

private val LOCATION_TYPES = listOf(
    "storage" to "Storage",
    "receiving" to "Receiving",
    "picking" to "Picking",
    "shipping" to "Shipping",
    "quality" to "Quality",
)

private val ADJUSTMENT_TYPES = listOf(
    "correction" to "Correction",
    "loss" to "Loss",
    "damage" to "Damage",
    "expiry" to "Expiry",
    "theft" to "Theft",
    "other" to "Other",
)

private fun InvStep.encode(): String = when (this) {
    InvStep.Hub -> "hub"
    InvStep.Warehouses -> "warehouses"
    is InvStep.WarehouseForm -> "wf:${id ?: "new"}"
    InvStep.Locations -> "locations"
    is InvStep.LocationForm -> "lf:${id ?: "new"}"
    InvStep.StockByLocation -> "stock_pick"
    is InvStep.LocationStock -> "stock:$locationId"
    InvStep.Adjustments -> "adjustments"
    is InvStep.AdjustmentForm -> "adjust_form"
    is InvStep.AdjustmentDetail -> "adjust:$id"
}

private fun decodeInvStep(raw: String): InvStep {
    val parts = raw.split(':', limit = 2)
    return when (parts[0]) {
        "hub" -> InvStep.Hub
        "warehouses" -> InvStep.Warehouses
        "wf" -> InvStep.WarehouseForm(parts.getOrNull(1)?.takeUnless { it == "new" }?.toLongOrNull())
        "locations" -> InvStep.Locations
        "lf" -> InvStep.LocationForm(parts.getOrNull(1)?.takeUnless { it == "new" }?.toLongOrNull())
        "stock_pick" -> InvStep.StockByLocation
        "stock" -> InvStep.LocationStock(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        "adjustments" -> InvStep.Adjustments
        "adjust_form" -> InvStep.AdjustmentForm()
        "adjust" -> InvStep.AdjustmentDetail(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        else -> InvStep.Hub
    }
}

private val InvStepSaver = Saver<InvStep, String>(
    save = { it.encode() },
    restore = { decodeInvStep(it) },
)

@Composable
fun InventoryScreen(container: AppContainer, onBack: () -> Unit) {
    var step by rememberSaveable(stateSaver = InvStepSaver) { mutableStateOf(InvStep.Hub) }

    when (val current = step) {
        InvStep.Hub -> InventoryHub(
            onBack = onBack,
            onNavigate = { step = it },
        )
        InvStep.Warehouses -> WarehouseListScreen(
            container = container,
            onBack = { step = InvStep.Hub },
            onCreate = { step = InvStep.WarehouseForm(null) },
            onEdit = { step = InvStep.WarehouseForm(it) },
        )
        is InvStep.WarehouseForm -> WarehouseFormScreen(
            container = container,
            warehouseId = current.id,
            onBack = { step = InvStep.Warehouses },
            onSaved = { step = InvStep.Warehouses },
        )
        InvStep.Locations -> LocationListScreen(
            container = container,
            onBack = { step = InvStep.Hub },
            onCreate = { step = InvStep.LocationForm(null) },
            onEdit = { step = InvStep.LocationForm(it) },
        )
        is InvStep.LocationForm -> LocationFormScreen(
            container = container,
            locationId = current.id,
            onBack = { step = InvStep.Locations },
            onSaved = { step = InvStep.Locations },
        )
        InvStep.StockByLocation -> StockLocationPickerScreen(
            container = container,
            onBack = { step = InvStep.Hub },
            onOpen = { step = InvStep.LocationStock(it) },
        )
        is InvStep.LocationStock -> LocationStockScreen(
            container = container,
            locationId = current.locationId,
            onBack = { step = InvStep.StockByLocation },
            onAdjust = { productId, productLabel, variationId, variationLabel, locId, qty, batch, rollNumber, isRoll, unit ->
                step = InvStep.AdjustmentForm(
                    productId = productId,
                    productLabel = productLabel,
                    variationValueId = variationId,
                    variationLabel = variationLabel,
                    locationId = locId,
                    currentQty = qty,
                    batchNumber = batch,
                    rollNumber = rollNumber,
                    isRoll = isRoll,
                    quantityUnitSuffix = unit,
                )
            },
        )
        InvStep.Adjustments -> AdjustmentListScreen(
            container = container,
            onBack = { step = InvStep.Hub },
            onCreate = { step = InvStep.AdjustmentForm() },
            onOpen = { step = InvStep.AdjustmentDetail(it) },
        )
        is InvStep.AdjustmentForm -> AdjustmentFormScreen(
            container = container,
            preset = current,
            onBack = {
                step = if (current.locationId != null) InvStep.LocationStock(current.locationId) else InvStep.Adjustments
            },
            onSaved = { step = InvStep.Adjustments },
        )
        is InvStep.AdjustmentDetail -> AdjustmentDetailScreen(
            container = container,
            adjustmentId = current.id,
            onBack = { step = InvStep.Adjustments },
            onChanged = { step = InvStep.Adjustments },
        )
    }
}

@Composable
private fun InventoryHub(onBack: () -> Unit, onNavigate: (InvStep) -> Unit) {
    val tiles = listOf(
        HubTile("Warehouses", "Create, edit & remove warehouses", Icons.Default.Store, InvStep.Warehouses),
        HubTile("Locations", "Bins, racks & zones per warehouse", Icons.Default.Place, InvStep.Locations),
        HubTile("Stock by location", "View on-hand qty at each bin", Icons.Default.Inventory, InvStep.StockByLocation),
        HubTile("Stock adjustments", "Fix wrong quantities on the floor", Icons.Default.Tune, InvStep.Adjustments),
    )
    ErpScaffold(title = "Inventory", subtitle = "Master data & on-hand stock", onBack = onBack) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles) { tile ->
                WorkflowTile(
                    title = tile.title,
                    description = tile.desc,
                    icon = tile.icon,
                    onClick = { onNavigate(tile.step) },
                )
            }
        }
    }
}

@Composable
private fun WarehouseListScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        val res = container.api.listWarehouses(page = page, perPage = 100)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Name", "Code", "Address", "Status")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { it.string("name") ?: "" },
                { it.string("code") ?: "" },
                { it.string("address") ?: "" },
                { if (it.boolean("is_active") != false) "active" else "inactive" },
            ),
        )
    }

    ErpScaffold(
        title = "Warehouses",
        subtitle = "Tap a row to edit",
        onBack = onBack,
        actions = {
            IconButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "Add warehouse")
            }
        },
    ) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search warehouses…",
        )
        visibleRows.forEach { row ->
            val id = row.long("id") ?: return@forEach
            ErpCard(onClick = { onEdit(id) }) {
                Text(row.string("name") ?: "—", fontWeight = FontWeight.SemiBold)
                Text(row.string("code") ?: "", style = MaterialTheme.typography.bodySmall)
                Text(
                    row.string("address") ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val active = row.boolean("is_active") != false
                Text(
                    if (active) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WarehouseFormScreen(
    container: AppContainer,
    warehouseId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val workspaceWh = container.workspaceContext().warehouseId

    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var warehouseType by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }
    var cityId by remember { mutableStateOf<Long?>(null) }
    var stateId by remember { mutableStateOf<Long?>(null) }
    var countryId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(warehouseId) {
        if (warehouseId == null) return@LaunchedEffect
        runCatching {
            val res = container.api.getWarehouse(warehouseId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            val row = envelopeObject(res) ?: error("Empty warehouse")
            name = row.string("name") ?: ""
            code = row.string("code") ?: ""
            address = row.string("address") ?: ""
            phone = row.string("phone") ?: ""
            email = row.string("email") ?: ""
            capacity = row.double("capacity")?.let { DisplayFormat.qty(it) } ?: ""
            warehouseType = row.string("warehouse_type") ?: ""
            isActive = row.boolean("is_active") != false
            cityId = row.long("city_id")
            stateId = row.long("state_id")
            countryId = row.long("country_id")
        }.onFailure { message = it.message }
    }

    fun buildRequest() = WarehouseUpsertRequest(
        name = name.trim(),
        code = code.trim(),
        address = address.trim(),
        city_id = cityId,
        state_id = stateId,
        country_id = countryId,
        phone = phone.ifBlank { null },
        email = email.ifBlank { null },
        capacity = capacity.toDoubleOrNull(),
        warehouse_type = warehouseType.ifBlank { null },
        is_active = isActive,
        copy_address_from_warehouse_id = if (warehouseId == null) workspaceWh else null,
    )

    ErpScaffold(
        title = if (warehouseId == null) "New warehouse" else "Edit warehouse",
        onBack = onBack,
        actions = {
            if (warehouseId != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        },
    ) {
        message?.let { StatusBanner(it, isError = it.contains("Error", true) || it.contains("Failed", true)) }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(code, { code = it }, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(capacity, { capacity = it }, label = { Text("Capacity") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(warehouseType, { warehouseType = it }, label = { Text("Type") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Active")
                Switch(isActive, { isActive = it })
            }
            if (warehouseId == null) {
                Text(
                    "Address details are copied from your current workspace warehouse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ErpPrimaryButton(
                text = if (warehouseId == null) "Create warehouse" else "Save changes",
                loading = loading,
                onClick = {
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val body = buildRequest()
                        val res = if (warehouseId == null) {
                            container.api.createWarehouse(body)
                        } else {
                            container.api.updateWarehouse(warehouseId, body)
                        }
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        onSaved()
                        null
                    }
                },
            )
        }
    }

    if (confirmDelete && warehouseId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete warehouse?") },
            text = { Text("Only unused warehouses can be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val res = container.api.deleteWarehouse(warehouseId)
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        onSaved()
                        null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LocationListScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val whId = container.workspaceContext().warehouseId
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        val res = container.api.listWarehouseLocations(
            warehouseId = whId,
            page = page,
            perPage = 200,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Location", "Zone", "Type", "Status")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { WorkflowJson.locationLabel(it) },
                { it.string("zone_name") ?: it.string("zone_code") ?: "" },
                { it.string("location_type") ?: "" },
                { if (it.boolean("is_active") != false) "active" else "inactive" },
            ),
        )
    }

    ErpScaffold(
        title = "Locations",
        subtitle = "Workspace warehouse",
        onBack = onBack,
        actions = {
            IconButton(onClick = onCreate) { Icon(Icons.Default.Add, contentDescription = "Add location") }
        },
    ) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search locations…",
        )
        visibleRows.forEach { row ->
            val id = row.long("id") ?: return@forEach
            ErpCard(onClick = { onEdit(id) }) {
                Text(WorkflowJson.locationLabel(row).ifBlank { row.string("zone_name") ?: "—" }, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(row.string("zone_code"), row.string("location_type")).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                val active = row.boolean("is_active") != false
                Text(
                    if (active) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LocationFormScreen(
    container: AppContainer,
    locationId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val defaultWh = container.workspaceContext().warehouseId

    var warehouseId by remember { mutableStateOf(defaultWh ?: 0L) }
    var zoneCode by remember { mutableStateOf("") }
    var zoneName by remember { mutableStateOf("") }
    var aisle by remember { mutableStateOf("") }
    var rack by remember { mutableStateOf("") }
    var shelf by remember { mutableStateOf("") }
    var bin by remember { mutableStateOf("") }
    var locationType by remember { mutableStateOf("storage") }
    var capacity by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }

    var warehousePickerOpen by remember { mutableStateOf(false) }
    var typePickerOpen by remember { mutableStateOf(false) }
    var warehouseOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var selectedWarehouse by remember { mutableStateOf<PickerOption?>(null) }
    var selectedType by remember { mutableStateOf(LOCATION_TYPES.first()) }

    LaunchedEffect(Unit) {
        runCatching {
            val res = container.api.listWarehouses(perPage = 100)
            if (res.isSuccessful) {
                warehouseOptions = envelopeList(res).mapNotNull(PickerMappers::warehouse)
                selectedWarehouse = warehouseOptions.firstOrNull { it.id == warehouseId }
            }
        }
    }

    LaunchedEffect(locationId) {
        if (locationId == null) return@LaunchedEffect
        runCatching {
            val res = container.api.getWarehouseLocation(locationId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            val row = envelopeObject(res) ?: error("Empty location")
            warehouseId = row.long("warehouse_id") ?: defaultWh ?: warehouseId
            zoneCode = row.string("zone_code") ?: ""
            zoneName = row.string("zone_name") ?: ""
            aisle = row.string("aisle") ?: ""
            rack = row.string("rack") ?: ""
            shelf = row.string("shelf") ?: ""
            bin = row.string("bin") ?: ""
            locationType = row.string("location_type") ?: "storage"
            capacity = row.double("capacity")?.let { DisplayFormat.qty(it) } ?: ""
            isActive = row.boolean("is_active") != false
            selectedType = LOCATION_TYPES.find { it.first == locationType } ?: LOCATION_TYPES.first()
            selectedWarehouse = warehouseOptions.firstOrNull { it.id == warehouseId }
        }.onFailure { message = it.message }
    }

    fun buildRequest() = WarehouseLocationUpsertRequest(
        warehouse_id = warehouseId,
        zone_code = zoneCode.trim(),
        zone_name = zoneName.trim(),
        aisle = aisle.ifBlank { null },
        rack = rack.ifBlank { null },
        shelf = shelf.ifBlank { null },
        bin = bin.ifBlank { null },
        location_type = locationType,
        capacity = capacity.toDoubleOrNull(),
        is_active = isActive,
    )

    ErpScaffold(
        title = if (locationId == null) "New location" else "Edit location",
        onBack = onBack,
        actions = {
            if (locationId != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        },
    ) {
        message?.let { StatusBanner(it, isError = true) }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchablePickerField(
                label = "Warehouse",
                selected = selectedWarehouse,
                placeholder = "Choose warehouse",
                onOpen = { warehousePickerOpen = true },
            )
            OutlinedTextField(zoneCode, { zoneCode = it }, label = { Text("Zone code") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(zoneName, { zoneName = it }, label = { Text("Zone name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(aisle, { aisle = it }, label = { Text("Aisle") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rack, { rack = it }, label = { Text("Rack") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(shelf, { shelf = it }, label = { Text("Shelf") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bin, { bin = it }, label = { Text("Bin") }, modifier = Modifier.fillMaxWidth())
            SearchablePickerField(
                label = "Location type",
                selected = PickerOption(0, selectedType.second),
                placeholder = "Type",
                onOpen = { typePickerOpen = true },
            )
            OutlinedTextField(capacity, { capacity = it }, label = { Text("Capacity") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Active")
                Switch(isActive, { isActive = it })
            }
            ErpPrimaryButton(
                text = if (locationId == null) "Create location" else "Save changes",
                loading = loading,
                onClick = {
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val body = buildRequest()
                        val res = if (locationId == null) {
                            container.api.createWarehouseLocation(body)
                        } else {
                            container.api.updateWarehouseLocation(locationId, body)
                        }
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        onSaved()
                        null
                    }
                },
            )
        }
    }

    if (warehousePickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "Warehouse",
            options = warehouseOptions,
            onDismiss = { warehousePickerOpen = false },
            onSelect = {
                selectedWarehouse = it
                warehouseId = it.id
                warehousePickerOpen = false
            },
        )
    }
    if (typePickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "Location type",
            options = LOCATION_TYPES.map { (code, label) -> PickerOption(code.hashCode().toLong(), label, code) },
            onDismiss = { typePickerOpen = false },
            onSelect = { option ->
                val match = LOCATION_TYPES.find { pair -> pair.second == option.title } ?: LOCATION_TYPES.first()
                selectedType = match
                locationType = match.first
                typePickerOpen = false
            },
        )
    }

    if (confirmDelete && locationId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete location?") },
            text = { Text("Locations with stock movements cannot be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val res = container.api.deleteWarehouseLocation(locationId)
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        onSaved()
                        null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StockLocationPickerScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val whId = container.workspaceContext().warehouseId
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        val res = container.api.listWarehouseLocations(warehouseId = whId, page = page, perPage = 200)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Location", "Zone name")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { WorkflowJson.locationLabel(it) },
                { it.string("zone_name") ?: "" },
            ),
        )
    }

    ErpScaffold(title = "Stock by location", subtitle = "Pick a bin to view stock", onBack = onBack) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search locations…",
        )
        visibleRows.forEach { row ->
            val id = row.long("id") ?: return@forEach
            ErpCard(onClick = { onOpen(id) }) {
                Text(WorkflowJson.locationLabel(row), fontWeight = FontWeight.SemiBold)
                Text(row.string("zone_name") ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LocationStockScreen(
    container: AppContainer,
    locationId: Long,
    onBack: () -> Unit,
    onAdjust: (
        Long,
        String,
        Long?,
        String?,
        Long,
        Double?,
        String?,
        String?,
        Boolean,
        String?,
    ) -> Unit,
) {
    var locationLabel by remember { mutableStateOf("Location") }
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        if (page == 1) {
            runCatching {
                val locRes = container.api.getWarehouseLocation(locationId)
                if (locRes.isSuccessful) {
                    envelopeObject(locRes)?.let { locationLabel = WorkflowJson.locationLabel(it) }
                }
            }
        }
        val res = container.api.listLocationStocks(locationId, page = page, perPage = 200)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Product", "SKU", "Variation", "Qty")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { it.productName() },
                { it.productSku() },
                { it.variationLabel() },
                {
                    val isRoll = it.isRollStockLine()
                    val qty = if (isRoll) it.double("roll_length") else it.double("quantity")
                    DisplayFormat.qty(qty ?: 0.0)
                },
            ),
            extraSearchText = { it.string("roll_number") ?: "" },
        )
    }

    ErpScaffold(title = locationLabel, subtitle = "On-hand stock", onBack = onBack) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search products…",
        )
        if (visibleRows.isEmpty() && !liveList.loading) {
            Text("No stock at this location.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleRows.forEach { row ->
            val isRoll = row.isRollStockLine()
            val qty = if (isRoll) row.double("roll_length") else row.double("quantity")
            val qtyLabel = formatQtyWithUnit(DisplayFormat.qty(qty ?: 0.0), row.quantityUnitSuffix(), isRoll)
            val productId = row.long("product_id") ?: return@forEach
            ErpCard {
                Text(row.productName(), fontWeight = FontWeight.SemiBold)
                row.variationLabel().takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                row.string("roll_number")?.takeIf { it.isNotBlank() }?.let {
                    Text("Roll $it", style = MaterialTheme.typography.bodySmall)
                }
                Text("On hand: $qtyLabel", style = MaterialTheme.typography.bodyMedium)
                ErpPrimaryButton(
                    text = "Adjust stock",
                    onClick = {
                        onAdjust(
                            productId,
                            row.productName(),
                            row.long("variation_value_id"),
                            row.variationLabel().takeIf { it.isNotBlank() },
                            locationId,
                            qty,
                            row.string("batch_number"),
                            row.string("roll_number"),
                            isRoll,
                            row.quantityUnitSuffix(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AdjustmentListScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val whId = container.workspaceContext().warehouseId
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        val res = container.api.listStockAdjustments(warehouseId = whId, page = page, perPage = 50)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Number", "Status", "Type", "Date")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { it.string("stock_adjustment_number") ?: "" },
                { it.string("status") ?: "" },
                { it.string("adjustment_type") ?: "" },
                { it.string("adjustment_date") ?: "" },
            ),
        )
    }

    ErpScaffold(
        title = "Adjustments",
        subtitle = "Pending & recent",
        onBack = onBack,
        actions = { IconButton(onClick = onCreate) { Icon(Icons.Default.Add, contentDescription = "New adjustment") } },
    ) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search adjustments…",
        )
        visibleRows.forEach { row ->
            val id = row.long("id") ?: return@forEach
            ErpCard(onClick = { onOpen(id) }) {
                Text(row.string("stock_adjustment_number") ?: "—", fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        DisplayFormat.status(row.string("status")),
                        DisplayFormat.status(row.string("adjustment_type")),
                        row.string("adjustment_date"),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AdjustmentFormScreen(
    container: AppContainer,
    preset: InvStep.AdjustmentForm,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val whId = container.workspaceContext().warehouseId
    if (whId == null) {
        ErpScaffold(title = "New adjustment", onBack = onBack) {
            StatusBanner("Select a workspace warehouse first.", isError = true)
        }
        return
    }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var selectedProduct by remember { mutableStateOf<PickerOption?>(null) }
    var selectedVariation by remember { mutableStateOf<PickerOption?>(null) }
    var variationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var selectedLocation by remember { mutableStateOf<PickerOption?>(null) }
    var selectedType by remember { mutableStateOf(ADJUSTMENT_TYPES.first()) }
    var reason by remember { mutableStateOf("Mobile stock correction") }
    var notes by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf(preset.batchNumber ?: "") }
    var rollNumber by remember { mutableStateOf(preset.rollNumber ?: "") }
    var isRollProduct by remember { mutableStateOf(preset.isRoll) }
    var quantityUnitSuffix by remember { mutableStateOf(preset.quantityUnitSuffix) }
    var currentQty by remember { mutableStateOf(preset.currentQty?.let { DisplayFormat.qty(it) } ?: "") }
    var newQty by remember { mutableStateOf("") }

    var productPickerOpen by remember { mutableStateOf(false) }
    var variationPickerOpen by remember { mutableStateOf(false) }
    var locationPickerOpen by remember { mutableStateOf(false) }
    var typePickerOpen by remember { mutableStateOf(false) }
    var productOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var locationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }

    val requiresVariation = variationOptions.isNotEmpty()
    val qtyLabel = if (isRollProduct) rollLengthLabel(quantityUnitSuffix) else "Current qty"
    val newQtyLabel = if (isRollProduct) {
        val suffix = quantityUnitSuffix?.takeIf { it.isNotBlank() }
        if (suffix != null) "New length ($suffix)" else "New length"
    } else {
        "New qty (target)"
    }

    suspend fun fetchStockForProduct(productId: Long) {
        val res = container.api.getProductStockQuantity(
            productId = productId,
            warehouseId = whId,
            variationId = selectedVariation?.id,
            warehouseLocationId = selectedLocation?.id,
            batchNumber = if (!isRollProduct) batchNumber.ifBlank { null } else null,
            rollNumber = if (isRollProduct) rollNumber.ifBlank { null } else null,
        )
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        val body = res.body()?.asJsonObject
        if (body?.boolean("requires_variation") == true) {
            currentQty = ""
            message = "Select a variation to load stock."
            return
        }
        isRollProduct = body?.boolean("is_roll") == true || isRollProduct
        quantityUnitSuffix = body?.string("quantity_unit_suffix") ?: quantityUnitSuffix
        body?.string("roll_number")?.takeIf { it.isNotBlank() }?.let { rollNumber = it }
        currentQty = DisplayFormat.qty(body?.double("quantity") ?: 0.0)
        message = null
    }

    fun loadVariations(productId: Long, preselectVariationId: Long? = null) {
        scope.launchWorkflow({ loading = it }, { message = it }) {
            val res = container.api.getProductVariations(productId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            val body = res.body()?.asJsonObject
            isRollProduct = body?.string("product_type") == "roll"
            val options = parseVariationPickerOptions(body?.getAsJsonArray("variations"))
            variationOptions = options
            selectedVariation = preselectVariationId?.let { id -> options.firstOrNull { it.id == id } }
            if (options.isEmpty()) {
                selectedVariation = null
                fetchStockForProduct(productId)
            } else if (selectedVariation != null) {
                fetchStockForProduct(productId)
            } else {
                currentQty = ""
            }
            null
        }
    }

    fun refreshCurrentQty() {
        val productId = selectedProduct?.id ?: return
        if (variationOptions.isNotEmpty() && selectedVariation == null) {
            currentQty = ""
            message = "Select a variation to load stock."
            return
        }
        scope.launchWorkflow({ loading = it }, { message = it }) {
            fetchStockForProduct(productId)
            null
        }
    }

    LaunchedEffect(preset.productId) {
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
        runCatching {
            val locRes = container.api.listWarehouseLocations(warehouseId = whId, perPage = 200)
            if (locRes.isSuccessful) {
                locationOptions = envelopeList(locRes).mapNotNull(PickerMappers::warehouseLocation)
                preset.locationId?.let { locId ->
                    selectedLocation = locationOptions.firstOrNull { it.id == locId }
                }
            }
        }
        if (preset.currentQty != null) {
            currentQty = DisplayFormat.qty(preset.currentQty)
        }
    }

    ErpScaffold(title = "New adjustment", onBack = onBack) {
        message?.let { StatusBanner(it, isError = true) }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        currentQty = ""
                    },
                )
            }
            SearchablePickerField(
                label = "Location",
                selected = selectedLocation,
                placeholder = "Optional location",
                onOpen = { locationPickerOpen = true },
                onClear = {
                    selectedLocation = null
                    refreshCurrentQty()
                },
            )
            SearchablePickerField(
                label = "Adjustment type",
                selected = PickerOption(0, selectedType.second),
                placeholder = "Type",
                onOpen = { typePickerOpen = true },
            )
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
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
                    label = { Text("Batch") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                currentQty,
                { currentQty = it },
                label = { Text(qtyLabel) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { refreshCurrentQty() }) {
                    Text(if (isRollProduct) "Refresh length" else "Refresh qty")
                }
            }
            OutlinedTextField(newQty, { newQty = it }, label = { Text(newQtyLabel) }, modifier = Modifier.fillMaxWidth())
            ErpPrimaryButton(
                text = "Submit adjustment",
                loading = loading,
                enabled = selectedProduct != null && newQty.isNotBlank(),
                onClick = {
                    val productId = selectedProduct?.id ?: return@ErpPrimaryButton
                    if (requiresVariation && selectedVariation == null) {
                        message = "Select a variation for this product."
                        return@ErpPrimaryButton
                    }
                    if (isRollProduct && rollNumber.isBlank()) {
                        message = "Enter the roll number for this roll product."
                        return@ErpPrimaryButton
                    }
                    val current = currentQty.toDoubleOrNull() ?: 0.0
                    val target = newQty.toDoubleOrNull() ?: return@ErpPrimaryButton
                    val delta = target - current
                    if (delta == 0.0) {
                        message = if (isRollProduct) "New length must differ from current." else "New quantity must differ from current."
                        return@ErpPrimaryButton
                    }
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val body = CreateStockAdjustmentRequest(
                            adjustment_date = LocalDate.now().toString(),
                            warehouse_id = whId,
                            adjustment_type = selectedType.first,
                            reason = reason.trim(),
                            notes = notes.ifBlank { null },
                            product_id = productId,
                            variation_value_id = selectedVariation?.id,
                            warehouse_location_id = selectedLocation?.id,
                            batch_number = if (!isRollProduct) batchNumber.ifBlank { null } else null,
                            roll_number = if (isRollProduct) rollNumber.ifBlank { null } else null,
                            current_quantity = current,
                            adjustment_quantity = delta,
                            new_quantity = target,
                        )
                        val res = container.api.createStockAdjustment(body)
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        val createdId = envelopeObject(res)?.long("id")
                        if (createdId != null) {
                            val approveRes = container.api.approveStockAdjustment(createdId)
                            if (!approveRes.isSuccessful) {
                                message = "Created pending approval: ${ApiErrorParser.httpMessage(approveRes)}"
                                onSaved()
                                return@launchWorkflow null
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
                currentQty = ""
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
                refreshCurrentQty()
            },
            searchHint = "Filter variations…",
        )
    }
    if (locationPickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "Location",
            options = locationOptions,
            onDismiss = { locationPickerOpen = false },
            onSelect = {
                selectedLocation = it
                locationPickerOpen = false
                refreshCurrentQty()
            },
        )
    }
    if (typePickerOpen) {
        SearchablePickerSheet(
            visible = true,
            title = "Adjustment type",
            options = ADJUSTMENT_TYPES.map { (code, label) -> PickerOption(code.hashCode().toLong(), label) },
            onDismiss = { typePickerOpen = false },
            onSelect = { option ->
                selectedType = ADJUSTMENT_TYPES.find { it.second == option.title } ?: ADJUSTMENT_TYPES.first()
                typePickerOpen = false
            },
        )
    }
}

private fun parseVariationPickerOptions(variations: JsonArray?): List<PickerOption> =
    variations?.mapNotNull { el ->
        val v = el.asJsonObject
        val id = v.long("id") ?: return@mapNotNull null
        val attrs = v.getAsJsonArray("descriptorValues") ?: v.getAsJsonArray("attributes")
        val attrText = attrs?.mapNotNull { a ->
            val ao = a.asJsonObject
            val name = ao.string("name")
                ?: ao.obj("variationDescriptor")?.string("name")
            val value = ao.string("value")
            if (!name.isNullOrBlank() && !value.isNullOrBlank()) "$name: $value" else null
        }?.joinToString(" · ")
        val main = v.string("value") ?: v.string("display_label")
        val title = listOfNotNull(main, attrText).joinToString(" — ").ifBlank { "Variation #$id" }
        PickerOption(id, title, attrText)
    } ?: emptyList()

@Composable
private fun AdjustmentDetailScreen(
    container: AppContainer,
    adjustmentId: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var adjustment by remember { mutableStateOf<JsonObject?>(null) }

    fun reload() {
        scope.launchWorkflow({ loading = it }, { message = it }) {
            val res = container.api.getStockAdjustment(adjustmentId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            adjustment = envelopeObject(res)
            null
        }
    }

    LaunchedEffect(adjustmentId) { reload() }

    val status = adjustment?.string("status")
    val number = adjustment?.string("stock_adjustment_number") ?: "Adjustment"
    val item = adjustment?.let { nestedItems(it, "items").firstOrNull() }

    ErpScaffold(title = number, subtitle = DisplayFormat.status(status), onBack = onBack) {
        message?.let { StatusBanner(it, isError = it.contains("Error", true) || it.contains("Failed", true)) }
        adjustment?.let { adj ->
            ErpCard {
                Text("Type: ${DisplayFormat.status(adj.string("adjustment_type"))}")
                Text("Reason: ${adj.string("reason") ?: "—"}")
                Text("Date: ${adj.string("adjustment_date") ?: "—"}")
            }
        }
        item?.let { line ->
            val isRollLine = line.isRollStockLine() ||
                line.obj("product")?.obj("product_type")?.string("type") == "roll"
            ErpCard {
                Text(line.productName(), fontWeight = FontWeight.SemiBold)
                line.variationLabel().takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                line.string("batch_number")?.takeIf { it.isNotBlank() }?.let { ref ->
                    Text(
                        if (isRollLine) "Roll $ref" else "Batch $ref",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val delta = line.double("adjustment_quantity")
                val unit = line.quantityUnitSuffix()
                val deltaLabel = if (isRollLine) {
                    formatQtyWithUnit(DisplayFormat.qty(delta ?: 0.0), unit, isRoll = true)
                } else {
                    DisplayFormat.qty(delta ?: 0.0)
                }
                Text(
                    if (isRollLine) "Length change: $deltaLabel" else "Qty change: $deltaLabel",
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (status == "pending") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ErpPrimaryButton(
                    text = "Approve",
                    loading = loading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launchWorkflow({ loading = it }, { message = it }) {
                            val res = container.api.approveStockAdjustment(adjustmentId)
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            onChanged()
                            null
                        }
                    },
                )
                ErpPrimaryButton(
                    text = "Reject",
                    loading = loading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launchWorkflow({ loading = it }, { message = it }) {
                            val res = container.api.rejectStockAdjustment(adjustmentId)
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            onChanged()
                            null
                        }
                    },
                )
            }
            ErpPrimaryButton(
                text = "Delete",
                loading = loading,
                onClick = {
                    scope.launchWorkflow({ loading = it }, { message = it }) {
                        val res = container.api.deleteStockAdjustment(adjustmentId)
                        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                        onChanged()
                        null
                    }
                },
            )
        }
    }
}
