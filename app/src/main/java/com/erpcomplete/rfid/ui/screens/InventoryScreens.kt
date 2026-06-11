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
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.erpcomplete.rfid.data.model.MobileInventoryPermissions
import com.erpcomplete.rfid.data.remote.CreateStockAdjustmentRequest
import com.erpcomplete.rfid.data.remote.WarehouseLocationUpsertRequest
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.LiveSyncIndicator
import com.erpcomplete.rfid.ui.components.LocationStockDetailSheet
import com.erpcomplete.rfid.ui.components.LocationStockLineCard
import com.erpcomplete.rfid.ui.components.SortableCardListToolbar
import com.erpcomplete.rfid.ui.components.UnmarkedLocationCard
import com.erpcomplete.rfid.ui.components.WarehouseLocationPickerCard
import com.erpcomplete.rfid.ui.components.AdjustmentDetailSkeleton
import com.erpcomplete.rfid.ui.components.WarehouseInfoSkeleton
import com.erpcomplete.rfid.ui.components.WorkflowFormSkeleton
import com.erpcomplete.rfid.ui.components.WorkflowListCardSkeleton
import com.erpcomplete.rfid.ui.components.applyCardListSortSearch
import com.erpcomplete.rfid.ui.components.rememberTableSortSearch
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.WorkflowTile
import com.erpcomplete.rfid.ui.permissions.rememberMobileInventoryPermissions
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
import com.erpcomplete.rfid.util.WorkflowJson.envelopeTotal
import com.erpcomplete.rfid.util.WorkflowJson.formatQtyWithUnit
import com.erpcomplete.rfid.util.WorkflowJson.isRollStockLine
import com.erpcomplete.rfid.util.WorkflowJson.onHandQuantity
import com.erpcomplete.rfid.util.WorkflowJson.rollFillStatus
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
import com.erpcomplete.rfid.util.StockLinePreset
import com.erpcomplete.rfid.util.UNMARKED_STOCK_LOCATION_ID
import com.erpcomplete.rfid.util.toStockLinePreset
import com.erpcomplete.rfid.util.launchWorkflow
import com.erpcomplete.rfid.util.toStockLinePreset
import com.erpcomplete.rfid.util.workspaceContext
import com.google.gson.JsonObject
import java.time.LocalDate

private sealed class InvStep {
    data object Hub : InvStep()
    data object Warehouses : InvStep()
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
    data object Relocations : InvStep()
    data class RelocationForm(val preset: RelocationFormPreset = RelocationFormPreset()) : InvStep()
    data class RelocationDetail(val id: Long) : InvStep()
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
    InvStep.Locations -> "locations"
    is InvStep.LocationForm -> "lf:${id ?: "new"}"
    InvStep.StockByLocation -> "stock_pick"
    is InvStep.LocationStock -> "stock:$locationId"
    InvStep.Adjustments -> "adjustments"
    is InvStep.AdjustmentForm -> "adjust_form"
    is InvStep.AdjustmentDetail -> "adjust:$id"
    InvStep.Relocations -> "relocations"
    is InvStep.RelocationForm -> "relocate_form"
    is InvStep.RelocationDetail -> "relocate:$id"
}

private fun decodeInvStep(raw: String): InvStep {
    val parts = raw.split(':', limit = 2)
    return when (parts[0]) {
        "hub" -> InvStep.Hub
        "warehouses", "wf" -> InvStep.Warehouses
        "locations" -> InvStep.Locations
        "lf" -> InvStep.LocationForm(parts.getOrNull(1)?.takeUnless { it == "new" }?.toLongOrNull())
        "stock_pick" -> InvStep.StockByLocation
        "stock" -> InvStep.LocationStock(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        "adjustments" -> InvStep.Adjustments
        "adjust_form" -> InvStep.AdjustmentForm()
        "adjust" -> InvStep.AdjustmentDetail(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        "relocations" -> InvStep.Relocations
        "relocate_form" -> InvStep.RelocationForm()
        "relocate" -> InvStep.RelocationDetail(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        else -> InvStep.Hub
    }
}

private val InvStepSaver = Saver<InvStep, String>(
    save = { it.encode() },
    restore = { decodeInvStep(it) },
)

@Composable
fun InventoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    initialPreset: StockLinePreset? = null,
    initialFlow: String? = null,
) {
    val permissions = rememberMobileInventoryPermissions(container)
    val deepLinkDeniedMessage = when {
        initialFlow == "adjust" && !permissions.stockAdjustment.create ->
            "You do not have permission to create stock adjustments."
        initialFlow == "relocate" && !permissions.stockRelocation.create ->
            "You do not have permission to create stock relocations."
        else -> null
    }
    if (deepLinkDeniedMessage != null) {
        ErpScaffold(title = "Inventory", onBack = onBack) {
            StatusBanner(deepLinkDeniedMessage, isError = true)
        }
        return
    }

    val openingStep = remember(initialPreset, initialFlow) {
        when (initialFlow) {
            "adjust" -> initialPreset?.let { adjustmentFormFromPreset(it) } ?: InvStep.AdjustmentForm()
            "relocate" -> initialPreset?.let { InvStep.RelocationForm(stockLinePresetToRelocationForm(it)) }
                ?: InvStep.RelocationForm()
            else -> null
        }
    }
    var step by rememberSaveable(stateSaver = InvStepSaver) {
        mutableStateOf(openingStep ?: InvStep.Hub)
    }

    when (val current = step) {
        InvStep.Hub -> InventoryHub(
            onBack = onBack,
            onNavigate = { step = it },
            permissions = permissions,
        )
        InvStep.Warehouses -> WarehouseInfoScreen(
            container = container,
            onBack = { step = InvStep.Hub },
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
            permissions = permissions,
            onBack = { step = InvStep.StockByLocation },
            onStockAction = { preset, flow ->
                step = when (flow) {
                    "relocate" -> InvStep.RelocationForm(stockLinePresetToRelocationForm(preset))
                    else -> adjustmentFormFromPreset(preset)
                }
            },
        )
        InvStep.Adjustments -> AdjustmentListScreen(
            container = container,
            canCreate = permissions.stockAdjustment.create,
            onBack = { step = InvStep.Hub },
            onCreate = { step = InvStep.AdjustmentForm() },
            onOpen = { step = InvStep.AdjustmentDetail(it) },
        )
        is InvStep.AdjustmentForm -> AdjustmentFormScreen(
            container = container,
            preset = current,
            onBack = {
                step = when (current.locationId) {
                    UNMARKED_STOCK_LOCATION_ID -> InvStep.LocationStock(UNMARKED_STOCK_LOCATION_ID)
                    null -> InvStep.Adjustments
                    else -> InvStep.LocationStock(current.locationId)
                }
            },
            onSaved = { step = InvStep.Adjustments },
        )
        is InvStep.AdjustmentDetail -> AdjustmentDetailScreen(
            container = container,
            adjustmentId = current.id,
            onBack = { step = InvStep.Adjustments },
            onChanged = { step = InvStep.Adjustments },
        )
        InvStep.Relocations -> RelocationListScreen(
            container = container,
            canCreate = permissions.stockRelocation.create,
            onBack = { step = InvStep.Hub },
            onCreate = { step = InvStep.RelocationForm() },
            onOpen = { step = InvStep.RelocationDetail(it) },
        )
        is InvStep.RelocationForm -> RelocationFormScreen(
            container = container,
            preset = current.preset,
            onBack = {
                step = when (current.preset.fromLocationId) {
                    UNMARKED_STOCK_LOCATION_ID -> InvStep.LocationStock(UNMARKED_STOCK_LOCATION_ID)
                    null -> InvStep.Relocations
                    else -> InvStep.LocationStock(current.preset.fromLocationId)
                }
            },
            onSaved = { step = InvStep.Relocations },
        )
        is InvStep.RelocationDetail -> RelocationDetailScreen(
            container = container,
            relocationId = current.id,
            onBack = { step = InvStep.Relocations },
            onChanged = { step = InvStep.Relocations },
        )
    }
}

@Composable
private fun InventoryHub(
    onBack: () -> Unit,
    onNavigate: (InvStep) -> Unit,
    permissions: MobileInventoryPermissions,
) {
    val tiles = buildList {
        add(HubTile("Warehouse", "Your current workspace warehouse", Icons.Default.Store, InvStep.Warehouses))
        add(HubTile("Locations", "Bins, racks & zones per warehouse", Icons.Default.Place, InvStep.Locations))
        add(HubTile("Stock by location", "View on-hand qty at each bin", Icons.Default.Inventory, InvStep.StockByLocation))
        if (permissions.stockAdjustment.read) {
            add(HubTile("Stock adjustments", "Fix wrong quantities on the floor", Icons.Default.Tune, InvStep.Adjustments))
        }
        if (permissions.stockRelocation.read) {
            add(HubTile("Stock relocations", "Move stock between bins", Icons.Default.SwapHoriz, InvStep.Relocations))
        }
    }
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
private fun WarehouseInfoScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val workspaceWhId = container.workspaceContext().warehouseId
    val workspaceWhName = container.authStore.warehouseNameBlocking()
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var warehouse by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(workspaceWhId) {
        loading = true
        warehouse = null
        message = null
        val whId = workspaceWhId
        if (whId == null) {
            message = "No warehouse selected. Choose a workspace from Home or Settings."
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            val res = container.api.getWarehouse(whId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            warehouse = envelopeObject(res) ?: error("Empty warehouse")
        }.onFailure { message = it.message }
        loading = false
    }

    ErpScaffold(
        title = warehouse?.string("name") ?: workspaceWhName ?: "Warehouse",
        subtitle = "Current workspace",
        onBack = onBack,
    ) {
        message?.let { StatusBanner(it, isError = it.contains("Error", true) || it.contains("Failed", true)) }
        when {
            loading -> WarehouseInfoSkeleton()
            warehouse == null && message == null -> {
                Text("Warehouse not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            warehouse != null -> {
                val row = warehouse!!
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ErpCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.string("name") ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            row.string("code")?.takeIf { it.isNotBlank() }?.let {
                                Text("Code: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            val active = row.boolean("is_active") != false
                            Text(
                                if (active) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    ErpCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            WarehouseInfoRow("Type", row.string("warehouse_type")?.let { DisplayFormat.status(it) })
                            WarehouseInfoRow("Address", row.string("address"))
                            WarehouseInfoRow("City / state / country", warehouseRegionLabel(row))
                            WarehouseInfoRow("Phone", row.string("phone"))
                            WarehouseInfoRow("Email", row.string("email"))
                            row.double("capacity")?.let { cap ->
                                WarehouseInfoRow("Capacity", DisplayFormat.qty(cap))
                            }
                            row.string("description")?.takeIf { it.isNotBlank() }?.let {
                                WarehouseInfoRow("Description", it)
                            }
                            row.obj("manager")?.string("name")?.let {
                                WarehouseInfoRow("Manager", it)
                            }
                        }
                    }
                    Text(
                        "Warehouse master data is managed on the web ERP. This device shows your assigned workspace only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun adjustmentFormFromPreset(preset: StockLinePreset) = InvStep.AdjustmentForm(
    productId = preset.productId,
    productLabel = preset.productLabel,
    variationValueId = preset.variationValueId,
    variationLabel = preset.variationLabel,
    locationId = preset.locationId,
    currentQty = preset.currentQty,
    batchNumber = preset.batchNumber,
    rollNumber = preset.rollNumber,
    isRoll = preset.isRoll,
    quantityUnitSuffix = preset.quantityUnitSuffix,
)

@Composable
private fun WarehouseInfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun warehouseRegionLabel(row: JsonObject): String? {
    val parts = listOfNotNull(
        row.obj("city")?.string("name"),
        row.obj("state")?.string("name"),
        row.obj("country")?.string("name"),
    ).filter { it.isNotBlank() }
    return parts.joinToString(", ").ifBlank { null }
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
        when {
            liveList.loading && visibleRows.isEmpty() -> WorkflowListCardSkeleton(5)
            visibleRows.isEmpty() -> Text("No locations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> visibleRows.forEach { row ->
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
    var formLoading by remember { mutableStateOf(locationId != null) }
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
        if (locationId == null) {
            formLoading = false
            return@LaunchedEffect
        }
        formLoading = true
        zoneCode = ""
        zoneName = ""
        aisle = ""
        rack = ""
        shelf = ""
        bin = ""
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
        formLoading = false
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
        if (formLoading) {
            WorkflowFormSkeleton(fieldCount = 8)
        } else {
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
    var unmarkedCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(whId) {
        unmarkedCount = null
        val id = whId ?: return@LaunchedEffect
        runCatching {
            val res = container.api.listUnlocatedStocks(id, page = 1, perPage = 1)
            if (res.isSuccessful) unmarkedCount = envelopeTotal(res)
        }
    }
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
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (whId != null) {
                UnmarkedLocationCard(
                    itemCount = unmarkedCount,
                    onClick = { onOpen(UNMARKED_STOCK_LOCATION_ID) },
                )
            }
            when {
                liveList.loading && visibleRows.isEmpty() -> WorkflowListCardSkeleton(5)
                visibleRows.isEmpty() -> Text("No bin locations found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> visibleRows.forEach { row ->
                    val id = row.long("id") ?: return@forEach
                    WarehouseLocationPickerCard(location = row, onClick = { onOpen(id) })
                }
            }
        }
    }
}

@Composable
private fun LocationStockScreen(
    container: AppContainer,
    locationId: Long,
    permissions: MobileInventoryPermissions,
    onBack: () -> Unit,
    onStockAction: (StockLinePreset, String) -> Unit,
) {
    val canAdjust = permissions.stockAdjustment.create
    val canRelocate = permissions.stockRelocation.create
    val isUnmarked = locationId == UNMARKED_STOCK_LOCATION_ID
    var locationLabel by remember(locationId) {
        mutableStateOf(if (isUnmarked) "Unmarked location" else "Location")
    }
    val sortSearch = rememberTableSortSearch()
    val liveList = rememberWorkflowLiveList(enabled = true) { page ->
        if (page == 1 && !isUnmarked) {
            runCatching {
                val locRes = container.api.getWarehouseLocation(locationId)
                if (locRes.isSuccessful) {
                    envelopeObject(locRes)?.let { locationLabel = WorkflowJson.locationLabel(it) }
                }
            }
        }
        val res = if (isUnmarked) {
            val whId = container.workspaceContext().warehouseId
                ?: error("No warehouse selected in workspace")
            container.api.listUnlocatedStocks(whId, page = page, perPage = 200)
        } else {
            container.api.listLocationStocks(locationId, page = page, perPage = 200)
        }
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        envelopePage(res, page)
    }
    val sortLabels = listOf("Product", "SKU", "Variation", "On hand", "Roll #")
    val visibleRows = remember(liveList.rows, sortSearch.searchQuery, sortSearch.sortColumnIndex, sortSearch.sortDirection) {
        liveList.rows.applyCardListSortSearch(
            sortSearch,
            listOf(
                { it.productName() },
                { it.productSku() },
                { it.variationLabel() },
                { DisplayFormat.qty(it.onHandQuantity()) },
                { it.string("roll_number") ?: "" },
            ),
            extraSearchText = { row ->
                buildString {
                    append(row.string("batch_number") ?: "")
                    append(' ')
                    append(row.rollFillStatus() ?: "")
                }
            },
        )
    }
    var selectedStock by remember { mutableStateOf<JsonObject?>(null) }
    var detailOpen by remember { mutableStateOf(false) }

    fun openStockAction(row: JsonObject, flow: String) {
        val preset = row.toStockLinePreset(if (isUnmarked) UNMARKED_STOCK_LOCATION_ID else locationId)
            ?: return
        onStockAction(preset, flow)
    }

    ErpScaffold(title = locationLabel, subtitle = "On-hand stock", onBack = onBack) {
        liveList.error?.let { StatusBanner(it, isError = true) }
        LiveSyncIndicator(liveList.lastUpdatedMs)
        SortableCardListToolbar(
            itemCount = visibleRows.size,
            sortSearch = sortSearch,
            sortLabels = sortLabels,
            searchPlaceholder = "Search product, SKU, variation, roll…",
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                liveList.loading && visibleRows.isEmpty() -> WorkflowListCardSkeleton(5)
                visibleRows.isEmpty() -> Text(
                    if (isUnmarked) "No unlocated stock in this warehouse." else "No stock at this location.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> visibleRows.forEach { row ->
                    LocationStockLineCard(
                        stock = row,
                        onClick = {
                            selectedStock = row
                            detailOpen = true
                        },
                    )
                }
            }
        }
    }

    LocationStockDetailSheet(
        visible = detailOpen,
        stock = selectedStock,
        locationLabel = if (isUnmarked) "Unmarked location" else locationLabel,
        canAdjust = canAdjust,
        canRelocate = canRelocate,
        onDismiss = {
            detailOpen = false
            selectedStock = null
        },
        onAdjust = { row ->
            detailOpen = false
            openStockAction(row, "adjust")
        },
        onRelocate = if (canRelocate) {
            { row ->
                detailOpen = false
                openStockAction(row, "relocate")
            }
        } else {
            null
        },
    )
}

@Composable
private fun AdjustmentListScreen(
    container: AppContainer,
    canCreate: Boolean,
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
        actions = {
            if (canCreate) {
                IconButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = "New adjustment")
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
            searchPlaceholder = "Search adjustments…",
        )
        when {
            liveList.loading && visibleRows.isEmpty() -> WorkflowListCardSkeleton(5)
            visibleRows.isEmpty() -> Text("No adjustments yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> visibleRows.forEach { row ->
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
            warehouseLocationId = selectedLocation?.id?.takeUnless { it == UNMARKED_STOCK_LOCATION_ID },
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
                    selectedLocation = if (locId == UNMARKED_STOCK_LOCATION_ID) {
                        PickerOption(UNMARKED_STOCK_LOCATION_ID, "Unmarked location")
                    } else {
                        locationOptions.firstOrNull { it.id == locId }
                    }
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
                            warehouse_location_id = selectedLocation?.id?.takeUnless { it == UNMARKED_STOCK_LOCATION_ID },
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
    var actionLoading by remember { mutableStateOf(false) }
    var detailLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var adjustment by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(adjustmentId) {
        adjustment = null
        detailLoading = true
        runCatching {
            val res = container.api.getStockAdjustment(adjustmentId)
            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
            adjustment = envelopeObject(res)
        }.onFailure { message = it.message }
        detailLoading = false
    }

    val status = adjustment?.string("status")
    val number = adjustment?.string("stock_adjustment_number") ?: "Adjustment"
    val item = adjustment?.let { nestedItems(it, "items").firstOrNull() }

    ErpScaffold(title = number, subtitle = DisplayFormat.status(status), onBack = onBack) {
        message?.let { StatusBanner(it, isError = it.contains("Error", true) || it.contains("Failed", true)) }
        if (detailLoading) {
            AdjustmentDetailSkeleton()
        } else {
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
                    loading = actionLoading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launchWorkflow({ actionLoading = it }, { message = it }) {
                            val res = container.api.approveStockAdjustment(adjustmentId)
                            if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                            onChanged()
                            null
                        }
                    },
                )
                ErpPrimaryButton(
                    text = "Reject",
                    loading = actionLoading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launchWorkflow({ actionLoading = it }, { message = it }) {
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
                loading = actionLoading,
                onClick = {
                    scope.launchWorkflow({ actionLoading = it }, { message = it }) {
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
}
