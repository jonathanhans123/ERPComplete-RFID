package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.ScanProfile
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.LocateProximityMeter
import com.erpcomplete.rfid.ui.components.PickerOption
import com.erpcomplete.rfid.ui.components.ScanResolveEntry
import com.erpcomplete.rfid.ui.components.ScanResolveStatus
import com.erpcomplete.rfid.ui.components.TagInfoDetailContent
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
import kotlinx.coroutines.launch

private data class LocateTagRow(
    val epc: String,
    val entry: ScanResolveEntry,
    val rssi: Short? = null,
)

@Composable
fun LocateScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
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
    val resolveCodes = remember(discoveryTags, selectedEpc, manualEpc, productTags) {
        buildSet {
            discoveryTags.forEach { add(it.epc) }
            productTags.forEach { add(it.epc) }
            selectedEpc?.let { add(it) }
            manualEpc.trim().uppercase().takeIf { it.isNotBlank() }?.let { add(it) }
        }.toList()
    }
    val resolveMap = rememberScanResolver(container.api, resolveCodes)

    val activeEpc = selectedEpc ?: manualEpc.trim().uppercase().takeIf { it.isNotBlank() }
    val activeDetail = activeEpc?.uppercase()?.let { resolveMap[it]?.detailInfo }
    val noTagsError = stringResource(R.string.locate_error_no_tags)

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
                    PickerOption(id, row.string("name") ?: context.getString(R.string.product_fallback_title, id), row.string("sku"))
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
                    val title = listOfNotNull(main, attrText).joinToString(" — ").ifBlank {
                        context.getString(R.string.variation_fallback_title, id)
                    }
                    PickerOption(id, title, attrText)
                } ?: emptyList()
                if (variationOptions.isEmpty()) selectedVariation = null
            }.onFailure { message = it.message }
            pickerLoading = false
        }
    }

    fun loadProductTags() {
        val productId = selectedProduct?.id ?: run {
            message = context.getString(R.string.locate_error_choose_product)
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
                val root = WorkflowJson.envelopeObject(res) ?: error(context.getString(R.string.error_empty_response))
                val tags = root.getAsJsonArray("tags")?.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val epc = obj.string("epc") ?: return@mapNotNull null
                    LocateTagRow(epc = epc, entry = scanResolveEntryFromTagInfo(obj))
                } ?: emptyList()
                if (tags.isEmpty()) error(context.getString(R.string.locate_error_no_tags))
                productTags = tags
                if (tags.size == 1) selectEpc(tags.first().epc)
                context.resources.getQuantityString(R.plurals.locate_tags_found, tags.size, tags.size)
            }.onSuccess {
                message = it
            }.onFailure {
                message = it.message
            }
            productTagsLoading = false
        }
    }

    ErpScaffold(
        title = stringResource(R.string.locate_title),
        subtitle = stringResource(R.string.locate_subtitle),
        onBack = onBack,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message?.let { StatusBanner(it, isError = it == noTagsError || it.contains(noTagsError, true)) }

            if (!isConnected) {
                StatusBanner(stringResource(R.string.locate_connect_required), isError = true)
            }

            Text(
                buildString {
                    append(
                        stringResource(
                            if (scanProfile == ScanProfile.DENSE) {
                                R.string.locate_profile_dense
                            } else {
                                R.string.locate_profile_range
                            },
                        ),
                    )
                    if (prefilterEnabled && activeEpc != null) {
                        append(stringResource(R.string.locate_epc_filter_target))
                    } else if (prefilterEnabled) {
                        append(stringResource(R.string.locate_epc_filter_prefix))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TabRow(modeTab) {
                Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text(stringResource(R.string.locate_tab_scan_first)) })
                Tab(selected = modeTab == 1, onClick = { modeTab = 1 }, text = { Text(stringResource(R.string.locate_tab_by_product)) })
            }

            when (modeTab) {
                0 -> {
                    Text(
                        stringResource(R.string.locate_scan_first_hint),
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
                                    ScanResolveStatus.PENDING,
                                ),
                                rssi = tag.rssi,
                            )
                        },
                        selectedEpc = activeEpc,
                        onSelect = { selectEpc(it) },
                        emptyText = stringResource(R.string.locate_empty_scan),
                    )
                }
                1 -> {
                    SearchablePickerField(
                        label = stringResource(R.string.label_product),
                        selected = selectedProduct,
                        placeholder = stringResource(R.string.placeholder_choose_product),
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
                            label = stringResource(R.string.label_variation),
                            selected = selectedVariation,
                            placeholder = if (variationOptions.isEmpty()) {
                                stringResource(R.string.placeholder_no_variations)
                            } else {
                                stringResource(R.string.placeholder_choose_variation_optional)
                            },
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
                            text = stringResource(R.string.locate_find_tags),
                            loading = productTagsLoading,
                            onClick = { loadProductTags() },
                        )
                        if (productTags.isNotEmpty()) {
                            Text(
                                stringResource(R.string.locate_registered_tags_header),
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
            Text(stringResource(R.string.locate_manual_epc_label), style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = manualEpc,
                onValueChange = {
                    manualEpc = it.uppercase()
                    selectedEpc = null
                },
                label = { Text(stringResource(R.string.label_epc)) },
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
                        Text(stringResource(R.string.locate_status_locating), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            activeEpc,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { clearSelection() }) { Text(stringResource(R.string.action_clear)) }
                }
                if (activeDetail != null && resolveMap[activeEpc.uppercase()]?.status == ScanResolveStatus.REGISTERED) {
                    TagInfoDetailContent(activeDetail, compact = true)
                }
                Text(
                    stringResource(R.string.locate_sweep_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LocateProximityMeter(
                    strength = strength,
                    isActive = scanning,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isConnected) {
                StatusBanner(stringResource(R.string.locate_select_tag_hint))
            }
        }
    }

    SearchablePickerSheet(
        visible = productPickerOpen,
        title = stringResource(R.string.label_product),
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
        searchHint = stringResource(R.string.search_product_hint),
    )
    SearchablePickerSheet(
        visible = variationPickerOpen,
        title = stringResource(R.string.label_variation),
        options = variationOptions,
        loading = pickerLoading,
        onDismiss = { variationPickerOpen = false },
        onSelect = {
            selectedVariation = it
            productTags = emptyList()
            clearSelection()
        },
        onSearch = { },
        searchHint = stringResource(R.string.label_variation),
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
            pluralStringResource(R.plurals.locate_scan_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onClear, enabled = count > 0) {
            androidx.compose.material3.Icon(Icons.Default.ClearAll, contentDescription = null)
            Text(stringResource(R.string.action_clear))
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
    val context = LocalContext.current
    val emDash = stringResource(R.string.display_empty)
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
        Text(stringResource(R.string.locate_col_epc), Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.locate_col_item), Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.locate_col_rssi), Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider()
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
        items(rows, key = { it.epc }) { row ->
            val selected = row.epc.equals(selectedEpc, ignoreCase = true)
            val bg = when {
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                row.entry.status == ScanResolveStatus.UNKNOWN ->
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
                    scanResolveLabel(context, row.entry),
                    Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
                Text(
                    row.rssi?.toString() ?: emDash,
                    Modifier.weight(0.4f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}
