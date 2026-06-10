package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.ScanProfile
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.LocateProximityMeter
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.ScanResolveEntry
import com.erpcomplete.rfid.ui.components.SearchablePickerField
import com.erpcomplete.rfid.ui.components.SearchablePickerSheet
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.rememberScanResolver
import com.erpcomplete.rfid.ui.components.scanResolveEntryFromTagInfo
import com.erpcomplete.rfid.ui.components.scanResolveLabel
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.WorkflowJson.long
import com.erpcomplete.rfid.util.WorkflowJson.string
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

private data class LocateTagRow(
    val epc: String,
    val entry: ScanResolveEntry,
    val rssi: Short? = null,
)

@Composable
fun LocateScreen(container: AppContainer, onBack: () -> Unit) {
    var modeTab by remember { mutableIntStateOf(0) }
    var selectedEpc by remember { mutableStateOf<String?>(null) }
    var manualEpc by remember { mutableStateOf("") }

    val strength by container.rfidManager.locateStrength.collectAsState()
    val scanning by container.rfidManager.locateScanning.collectAsState()
    val isConnected by container.rfidManager.isDeviceConnected.collectAsState()
    val discoveryTags by container.rfidManager.scannedTags.collectAsState()
    val scanProfile by container.rfidSettingsStore.scanProfile.collectAsState(initial = ScanProfile.RANGE)
    val prefilterEnabled by container.rfidSettingsStore.epcPrefilterEnabled.collectAsState(initial = true)

    var selectedProduct by remember { mutableStateOf<PickerOption?>(null) }
    var selectedVariation by remember { mutableStateOf<PickerOption?>(null) }
    var productPickerOpen by remember { mutableStateOf(false) }
    var variationPickerOpen by remember { mutableStateOf(false) }
    var productOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var variationOptions by remember { mutableStateOf<List<PickerOption>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }
    var productTags by remember { mutableStateOf<List<LocateTagRow>>(emptyList()) }
    var productTagsLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val resolveMap = rememberScanResolver(container.api, discoveryTags.map { it.epc })

    val activeEpc = selectedEpc ?: manualEpc.trim().uppercase().takeIf { it.isNotBlank() }

    LaunchedEffect(activeEpc) {
        container.rfidManager.setLocateTarget(activeEpc)
    }
    DisposableEffect(Unit) {
        container.rfidManager.clearScannedTags()
        onDispose {
            container.rfidManager.setLocateTarget(null)
            container.rfidManager.clearScannedTags()
        }
    }

    fun selectEpc(epc: String) {
        selectedEpc = epc.trim().uppercase()
        manualEpc = selectedEpc!!
    }

    fun clearSelection() {
        selectedEpc = null
        manualEpc = ""
        container.rfidManager.setLocateTarget(null)
    }

    fun loadProducts(query: String) {
        scope.launch {
            pickerLoading = true
            runCatching {
                val res = container.api.listProducts(search = query.ifBlank { null }, perPage = 60)
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                productOptions = WorkflowJson.envelopeList(res).mapNotNull { row ->
                    val id = row.long("id") ?: return@mapNotNull null
                    PickerOption(id, row.string("name") ?: "Product #$id", row.string("sku"))
                }
            }.onFailure { message = it.message }
            pickerLoading = false
        }
    }

    fun loadVariations(productId: Long) {
        scope.launch {
            pickerLoading = true
            runCatching {
                val res = container.api.getProductVariations(productId)
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                val body = res.body()?.asJsonObject
                variationOptions = body?.getAsJsonArray("variations")?.mapNotNull { el ->
                    val v = el.asJsonObject
                    val id = v.long("id") ?: return@mapNotNull null
                    val attrs = v.getAsJsonArray("descriptorValues") ?: v.getAsJsonArray("attributes")
                    val attrText = attrs?.mapNotNull { a ->
                        val ao = a.asJsonObject
                        val name = ao.get("name")?.asString
                            ?: ao.getAsJsonObject("variationDescriptor")?.get("name")?.asString
                        val value = ao.get("value")?.asString
                        if (!name.isNullOrBlank() && !value.isNullOrBlank()) "$name: $value" else null
                    }?.joinToString(" · ")
                    val main = v.get("value")?.asString ?: v.get("display_label")?.asString
                    val title = listOfNotNull(main, attrText).joinToString(" — ").ifBlank { "Variation #$id" }
                    PickerOption(id, title, attrText)
                } ?: emptyList()
                if (variationOptions.isEmpty()) selectedVariation = null
            }.onFailure { message = it.message }
            pickerLoading = false
        }
    }

    fun loadProductTags() {
        val productId = selectedProduct?.id ?: run {
            message = "Choose a product first"
            return
        }
        scope.launch {
            productTagsLoading = true
            productTags = emptyList()
            runCatching {
                val res = container.api.listRfidTags(
                    productId = productId,
                    variationValueId = selectedVariation?.id,
                )
                if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
                val root = WorkflowJson.envelopeObject(res) ?: error("Empty response")
                val tags = root.getAsJsonArray("tags")?.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val epc = obj.string("epc") ?: return@mapNotNull null
                    LocateTagRow(epc = epc, entry = scanResolveEntryFromTagInfo(obj))
                } ?: emptyList()
                if (tags.isEmpty()) error("No registered tags for this product")
                productTags = tags
                if (tags.size == 1) selectEpc(tags.first().epc)
                message = "${tags.size} tag${if (tags.size == 1) "" else "s"} found — tap one to locate"
            }.onFailure {
                message = it.message
            }
            productTagsLoading = false
        }
    }

    ErpScaffold(
        title = "Locate item",
        subtitle = "Identify a tag, then sweep until green",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message?.let { StatusBanner(it, isError = it.contains("No registered", true)) }

            if (!isConnected) {
                StatusBanner("Connect an RFD90 on the Connect tab first.", isError = true)
            }

            Text(
                buildString {
                    append("RF profile: ")
                    append(if (scanProfile == ScanProfile.DENSE) "Dense (Settings)" else "Range (Settings)")
                    if (prefilterEnabled && activeEpc != null) append(" · EPC filter on target")
                    else if (prefilterEnabled) append(" · EPC prefix filter")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TabRow(modeTab) {
                Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text("Scan first") })
                Tab(selected = modeTab == 1, onClick = { modeTab = 1 }, text = { Text("By product") })
            }

            when (modeTab) {
                0 -> {
                    Text(
                        "Hold top trigger to read nearby tags, then tap a row to pick which EPC to locate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LocateTagListHeader(
                        count = discoveryTags.size,
                        onClear = {
                            container.rfidManager.clearScannedTags()
                            clearSelection()
                        },
                    )
                    LocateSelectableTagList(
                        rows = discoveryTags.map { tag ->
                            LocateTagRow(
                                epc = tag.epc,
                                entry = resolveMap[tag.epc.uppercase()] ?: ScanResolveEntry(
                                    com.erpcomplete.rfid.ui.components.ScanResolveStatus.PENDING,
                                ),
                                rssi = tag.rssi,
                            )
                        },
                        selectedEpc = activeEpc,
                        onSelect = { selectEpc(it) },
                        emptyText = "Hold top trigger to scan tags in front of you.",
                    )
                }
                1 -> {
                    SearchablePickerField(
                        label = "Product",
                        selected = selectedProduct,
                        placeholder = "Choose product",
                        onOpen = {
                            productPickerOpen = true
                            loadProducts("")
                        },
                        onClear = {
                            selectedProduct = null
                            selectedVariation = null
                            productTags = emptyList()
                            clearSelection()
                        },
                    )
                    if (selectedProduct != null) {
                        SearchablePickerField(
                            label = "Variation",
                            selected = selectedVariation,
                            placeholder = if (variationOptions.isEmpty()) "No variations" else "Choose variation (optional)",
                            onOpen = {
                                selectedProduct?.id?.let { loadVariations(it) }
                                variationPickerOpen = true
                            },
                            onClear = {
                                selectedVariation = null
                                productTags = emptyList()
                                clearSelection()
                            },
                        )
                        ErpPrimaryButton(
                            text = "Find registered tags",
                            loading = productTagsLoading,
                            onClick = { loadProductTags() },
                        )
                        if (productTags.isNotEmpty()) {
                            Text(
                                "Registered tags — tap to locate",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LocateSelectableTagList(
                                rows = productTags,
                                selectedEpc = activeEpc,
                                onSelect = { selectEpc(it) },
                                emptyText = "",
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Or enter EPC directly", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = manualEpc,
                onValueChange = {
                    manualEpc = it.uppercase()
                    selectedEpc = null
                },
                label = { Text("EPC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isConnected,
            )

            if (activeEpc != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Locating", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            activeEpc,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { clearSelection() }) { Text("Clear") }
                }
                Text(
                    "Hold top trigger and sweep · release to stop · green = closer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LocateProximityMeter(
                    strength = strength,
                    isActive = scanning,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isConnected) {
                StatusBanner("Select or scan a tag above, then hold top trigger to locate it.")
            }
        }
    }

    SearchablePickerSheet(
        visible = productPickerOpen,
        title = "Product",
        options = productOptions,
        loading = pickerLoading,
        onDismiss = { productPickerOpen = false },
        onSelect = {
            selectedProduct = it
            selectedVariation = null
            productTags = emptyList()
            clearSelection()
            it.id.let { id -> loadVariations(id) }
        },
        onSearch = { loadProducts(it) },
        searchHint = "Search product…",
    )
    SearchablePickerSheet(
        visible = variationPickerOpen,
        title = "Variation",
        options = variationOptions,
        loading = pickerLoading,
        onDismiss = { variationPickerOpen = false },
        onSelect = {
            selectedVariation = it
            productTags = emptyList()
            clearSelection()
        },
        onSearch = { },
        searchHint = "Variation",
    )
}

@Composable
private fun LocateTagListHeader(count: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count scan${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onClear, enabled = count > 0) {
            androidx.compose.material3.Icon(Icons.Default.ClearAll, contentDescription = null)
            Text("Clear")
        }
    }
}

@Composable
private fun LocateSelectableTagList(
    rows: List<LocateTagRow>,
    selectedEpc: String?,
    onSelect: (String) -> Unit,
    emptyText: String,
) {
    if (rows.isEmpty() && emptyText.isNotBlank()) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("EPC", Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("Item", Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("RSSI", Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider()
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
        items(rows, key = { it.epc }) { row ->
            val selected = row.epc.equals(selectedEpc, ignoreCase = true)
            val bg = when {
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                row.entry.status == com.erpcomplete.rfid.ui.components.ScanResolveStatus.UNKNOWN ->
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                else -> Color.Transparent
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .clickable { onSelect(row.epc) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.epc.takeLast(12),
                    Modifier.weight(1.2f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    scanResolveLabel(row.entry),
                    Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
                Text(
                    row.rssi?.toString() ?: "—",
                    Modifier.weight(0.4f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}
