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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
import androidx.navigation.NavHostController
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.ResolveRequest
import com.erpcomplete.rfid.rfid.RfidManager
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.ScanResolveStatus
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.components.TagInfoDetailContent
import com.erpcomplete.rfid.ui.components.TagRegistrationSheet
import com.erpcomplete.rfid.ui.components.rememberScanResolver
import com.erpcomplete.rfid.ui.permissions.rememberMobileInventoryPermissions
import com.erpcomplete.rfid.ui.components.scanResolveLabel
import com.erpcomplete.rfid.ui.navigation.navigateWorkflow
import com.erpcomplete.rfid.util.buildInventoryDeepLinkUri
import com.erpcomplete.rfid.util.toStockLinePreset
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer, innerNav: NavHostController) {
    val context = LocalContext.current
    val permissions = rememberMobileInventoryPermissions(container)
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
        title = stringResource(R.string.search_title),
        subtitle = stringResource(R.string.search_subtitle),
    ) {
        if (!isConnected) {
            StatusBanner(stringResource(R.string.search_connect_required), isError = true)
        } else {
            StatusBanner(stringResource(R.string.search_scan_hint))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    pluralStringResource(R.plurals.search_unique_count, scans.size, scans.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (unknownCount > 0) {
                    Text(
                        stringResource(R.string.scan_unknown_erp_hint_register, unknownCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { rfid.clearSearchScans() }, enabled = scans.isNotEmpty()) {
                androidx.compose.material3.Icon(Icons.Default.ClearAll, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text(stringResource(R.string.action_clear))
            }
        }

        ErpCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (scans.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.search_empty),
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
                    Text(stringResource(R.string.search_col_code), Modifier.weight(1.5f), style = searchTableHeaderStyle())
                    Text(stringResource(R.string.search_col_erp), Modifier.weight(1.2f), style = searchTableHeaderStyle())
                    Text(stringResource(R.string.search_col_type), Modifier.weight(0.5f), style = searchTableHeaderStyle())
                    Text(stringResource(R.string.search_col_seen), Modifier.weight(0.6f), style = searchTableHeaderStyle())
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(scans, key = { it.code }) { row ->
                        val entry = resolveMap[row.code.uppercase()]
                        SearchTableRow(
                            row = row,
                            erpLabel = scanResolveLabel(context, entry),
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
                                                if (!res.isSuccessful) error(context.getString(R.string.search_error_lookup_failed))
                                                val body = res.body()?.data
                                                if (body?.resolved != true) error(context.getString(R.string.search_error_not_registered))
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
                Text(stringResource(R.string.search_tag_details_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        ErpPrimaryButton(text = stringResource(R.string.search_register_tag), onClick = {
                            registerCode = row.code
                            selectedRow = null
                        })
                    }
                    detailInfo != null -> {
                        TagInfoDetailContent(detailInfo!!)
                        val preset = detailInfo!!.toStockLinePreset()
                        if (preset != null && (permissions.stockAdjustment.create || permissions.stockRelocation.create)) {
                            Spacer(Modifier.height(16.dp))
                            if (permissions.stockAdjustment.create) {
                                ErpPrimaryButton(
                                    text = stringResource(R.string.action_adjust_stock),
                                    onClick = {
                                        selectedRow = null
                                        innerNav.navigateWorkflow(buildInventoryDeepLinkUri("adjust", preset))
                                    },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            if (permissions.stockRelocation.create) {
                                OutlinedButton(
                                    onClick = {
                                        selectedRow = null
                                        innerNav.navigateWorkflow(buildInventoryDeepLinkUri("relocate", preset))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_relocate_bin))
                                }
                            }
                        }
                    }
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
    val typeLabel = if (row.type == RfidManager.ScanType.RFID) {
        stringResource(R.string.scan_type_rfid)
    } else {
        stringResource(R.string.scan_type_barcode)
    }
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
        Text(typeLabel, Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium)
        Text(timeFmt.format(Date(row.lastSeenAt)), Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun searchTableHeaderStyle() = MaterialTheme.typography.labelMedium.copy(
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
