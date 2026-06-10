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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.ResolveRequest
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.ScanResolveStatus
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.TagRegistrationSheet
import com.erpcomplete.rfid.ui.components.rememberScanResolver
import com.erpcomplete.rfid.ui.components.scanResolveLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer) {
    val rfid = container.rfidManager
    val scans by rfid.searchScans.collectAsState()
    val isConnected by rfid.isDeviceConnected.collectAsState()
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    val resolveMap = rememberScanResolver(container.api, scans.map { it.code }, refreshKey)
    val unknownCount = scans.count { resolveMap[it.code.uppercase()]?.status == ScanResolveStatus.UNKNOWN }

    var selectedRow by remember { mutableStateOf<RfidManager.SearchScanRow?>(null) }
    var registerCode by remember { mutableStateOf<String?>(null) }
    var detailInfo by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ErpScaffold(
        title = "Search",
        subtitle = "Scan RFID or barcode — unknown tags can be registered",
    ) {
        if (!isConnected) {
            StatusBanner("Connect an RFD90 on the Connect tab to start scanning.", isError = true)
        } else {
            StatusBanner("Hold top trigger for RFID. Release to stop.")
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${scans.size} unique scan${if (scans.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (unknownCount > 0) {
                    Text(
                        "$unknownCount not in ERP — tap red row to register",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { rfid.clearSearchScans() }, enabled = scans.isNotEmpty()) {
                androidx.compose.material3.Icon(Icons.Default.ClearAll, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("Clear")
            }
        }

        ErpCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (scans.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No tags yet — scan to populate this table.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Code", Modifier.weight(1.5f), style = searchTableHeaderStyle())
                    Text("ERP", Modifier.weight(1.2f), style = searchTableHeaderStyle())
                    Text("Type", Modifier.weight(0.5f), style = searchTableHeaderStyle())
                    Text("Seen", Modifier.weight(0.6f), style = searchTableHeaderStyle())
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(scans, key = { it.code }) { row ->
                        val entry = resolveMap[row.code.uppercase()]
                        SearchTableRow(
                            row = row,
                            erpLabel = scanResolveLabel(entry),
                            isUnknown = entry?.status == ScanResolveStatus.UNKNOWN,
                            onClick = {
                                when (entry?.status) {
                                    ScanResolveStatus.UNKNOWN -> registerCode = row.code
                                    ScanResolveStatus.REGISTERED -> {
                                        selectedRow = row
                                        detailInfo = null
                                        detailError = null
                                        detailLoading = true
                                        scope.launch {
                                            runCatching {
                                                val res = container.api.resolve(ResolveRequest(row.code))
                                                if (!res.isSuccessful) error("Lookup failed")
                                                val body = res.body()?.data
                                                if (body?.resolved != true) error("Not registered")
                                                body.info
                                            }.onSuccess {
                                                detailInfo = it
                                                detailLoading = false
                                            }.onFailure {
                                                detailError = it.message
                                                detailLoading = false
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    TagRegistrationSheet(
        visible = registerCode != null,
        code = registerCode,
        container = container,
        onDismiss = { registerCode = null },
        onRegistered = { refreshKey++ },
    )

    selectedRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedRow = null
                detailInfo = null
                detailError = null
            },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Tag details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    row.code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    detailLoading -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    detailError != null -> {
                        StatusBanner(detailError!!, isError = true)
                        ErpPrimaryButton(text = "Register this tag", onClick = {
                            registerCode = row.code
                            selectedRow = null
                        })
                    }
                    detailInfo != null -> TagDetailContent(detailInfo!!)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SearchTableRow(
    row: RfidManager.SearchScanRow,
    erpLabel: String,
    isUnknown: Boolean,
    onClick: () -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val bg = if (isUnknown) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.code, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2)
        Text(erpLabel, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text(if (row.type == RfidManager.ScanType.RFID) "RFID" else "BC", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium)
        Text(timeFmt.format(Date(row.lastSeenAt)), Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TagDetailContent(info: Map<String, Any?>) {
    val product = info.nestedMap("product")
    val variation = info.nestedMap("variation")
    val stock = info.nestedMap("stock")
    val warehouse = info.nestedMap("warehouse")
    val location = info.nestedMap("warehouse_location")
    val descriptors = info.listOfMaps("variation_descriptors")
    val outputLabel = info.nestedMap("output_label")

    DetailSection("Product") {
        DetailLine("Name", product?.string("name"))
        DetailLine("SKU", product?.string("sku"))
    }

    if (variation != null || descriptors.isNotEmpty()) {
        DetailSection("Variation") {
            DetailLine("Name", variation?.string("name") ?: variation?.string("value"))
            DetailLine("SKU", variation?.string("sku"))
            descriptors.forEach { d ->
                DetailLine(d.string("name") ?: "Option", d.string("value"))
            }
        }
    }

    if (stock != null) {
        DetailSection("Stock") {
            val isRoll = info["product_type"]?.toString() == "roll"
                || !stock.string("roll_number").isNullOrBlank()
                || (stock["roll_length"]?.toString()?.toDoubleOrNull() ?: 0.0) > 0.0
            if (isRoll) {
                DetailLine("Roll #", stock.string("roll_number"))
                DetailLine("Roll length", stock["roll_length"]?.toString())
            } else {
                DetailLine("Quantity", stock["quantity"]?.toString())
            }
            DetailLine("Batch", stock.string("batch_number"))
            DetailLine("Type", stock.string("stock_type"))
        }
    }

    DetailSection("Location") {
        DetailLine("Warehouse", warehouse?.string("name"))
        DetailLine("Location", location?.string("name"))
        DetailLine("Location barcode", location?.string("barcode"))
    }

    if (outputLabel != null) {
        DetailSection("Label") {
            DetailLine("Barcode", outputLabel.string("barcode_value"))
            DetailLine("Status", outputLabel.string("status"))
        }
    }

    DetailSection("Tag") {
        DetailLine("EPC", info.string("epc"))
        DetailLine("Status", info.string("status"))
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    ErpCard { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun searchTableHeaderStyle() = MaterialTheme.typography.labelMedium.copy(
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.nestedMap(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.listOfMaps(key: String): List<Map<String, Any?>> {
    val raw = this[key] as? List<*> ?: return emptyList()
    return raw.mapNotNull { it as? Map<String, Any?> }
}

private fun Map<String, Any?>?.string(key: String): String? =
    this?.get(key)?.toString()?.takeIf { it.isNotBlank() }
