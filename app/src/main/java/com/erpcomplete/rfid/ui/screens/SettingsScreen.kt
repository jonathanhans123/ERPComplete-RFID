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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.erpcomplete.rfid.BuildConfig
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.ScanProfile
import com.erpcomplete.rfid.rfid.ReaderDiagnostics
import com.erpcomplete.rfid.rfid.FirmwareUpdateState
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.FirmwareUpdateMeter
import com.erpcomplete.rfid.ui.components.ReaderSerialBlock
import com.erpcomplete.rfid.ui.components.ScannerBatteryMeter
import com.erpcomplete.rfid.ui.components.WorkflowTile
import com.erpcomplete.rfid.ui.components.WorkspaceContextCard
import com.erpcomplete.rfid.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    rootNavController: NavHostController,
) {
    val email by container.authStore.userEmail.collectAsState(initial = null)
    val warehouseName by container.authStore.warehouseName.collectAsState(initial = null)
    val buName by container.authStore.businessUnitName.collectAsState(initial = null)
    val teamName by container.authStore.teamName.collectAsState(initial = null)
    val scanProfile by container.rfidSettingsStore.scanProfile.collectAsState(initial = ScanProfile.RANGE)
    val prefilterEnabled by container.rfidSettingsStore.epcPrefilterEnabled.collectAsState(initial = true)
    val companyPrefix by container.rfidSettingsStore.epcCompanyPrefix.collectAsState(initial = null)
    val diagnostics by container.rfidManager.diagnostics.collectAsState()
    val isConnected by container.rfidManager.isDeviceConnected.collectAsState()
    val firmwareUpdate by container.rfidManager.firmwareUpdate.collectAsState()
    val autoFirmwareCheck by container.rfidSettingsStore.autoFirmwareCheck.collectAsState(initial = true)
    val apiBaseUrl by container.apiSettingsStore.baseUrl.collectAsState(initial = BuildConfig.API_BASE_URL)
    val failedSync by container.syncRepository.failedItems.collectAsState()
    val pendingSync by container.syncRepository.pendingCount.collectAsState()

    var prefixDraft by remember { mutableStateOf(companyPrefix.orEmpty()) }
    var apiUrlDraft by remember { mutableStateOf(apiBaseUrl) }
    var voidEpc by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmWorkspaceChange by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val prefixDirty = prefixDraft != companyPrefix.orEmpty()

    LaunchedEffect(companyPrefix) {
        prefixDraft = companyPrefix.orEmpty()
    }

    LaunchedEffect(apiBaseUrl) {
        apiUrlDraft = apiBaseUrl
    }

    LaunchedEffect(isConnected) {
        if (isConnected) container.rfidManager.refreshDiagnostics()
    }

    if (confirmWorkspaceChange) {
        AlertDialog(
            onDismissRequest = { confirmWorkspaceChange = false },
            title = { Text("Change workspace?") },
            text = { Text("Unsaved work on open screens may be lost. Continue to pick another warehouse?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWorkspaceChange = false
                        scope.launch {
                            container.authStore.clearWorkspace()
                            rootNavController.navigate(Routes.WORKSPACE) {
                                popUpTo(Routes.MAIN) { inclusive = true }
                            }
                        }
                    },
                ) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWorkspaceChange = false }) { Text("Cancel") }
            },
        )
    }

    ErpScaffold(title = "Settings", subtitle = "Device & workspace") {
        message?.let {
            com.erpcomplete.rfid.ui.components.StatusBanner(
                it,
                isError = it.contains("error", true) || it.contains("fail", true),
            )
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WorkspaceContextCard(
                warehouseName = warehouseName,
                businessUnitName = buName,
                teamName = teamName,
                onChangeClick = { confirmWorkspaceChange = true },
            )
            email?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }

            SettingsSectionLabel("RFID reader")

            ErpCard {
                SettingsCardHeader(
                    icon = Icons.Default.Tune,
                    title = "Scan profile",
                    subtitle = "Power and link rate for inventory",
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScanProfileOption(
                        title = "Range",
                        description = "Max distance",
                        selected = scanProfile == ScanProfile.RANGE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                container.rfidSettingsStore.setScanProfile(ScanProfile.RANGE)
                                container.rfidManager.applySettingsToReader()
                            }
                        },
                    )
                    ScanProfileOption(
                        title = "Dense",
                        description = "Crowded aisles",
                        selected = scanProfile == ScanProfile.DENSE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                container.rfidSettingsStore.setScanProfile(ScanProfile.DENSE)
                                container.rfidManager.applySettingsToReader()
                            }
                        },
                    )
                }
            }

            ErpCard {
                SettingsCardHeader(
                    icon = Icons.Default.FilterAlt,
                    title = "EPC pre-filter",
                    subtitle = "Hardware mask for company tag prefix",
                )
                Spacer(Modifier.height(8.dp))
                SettingsToggleRow(
                    label = "Enable mask on reader",
                    checked = prefilterEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.rfidSettingsStore.setEpcPrefilterEnabled(enabled)
                            container.rfidManager.applySettingsToReader()
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = prefixDraft,
                    onValueChange = { prefixDraft = it.uppercase().filter { ch -> ch in "0123456789ABCDEF" } },
                    label = { Text("Company EPC prefix") },
                    placeholder = { Text("3034257B") },
                    supportingText = { Text("Hex only — used when no locate target is set") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
                if (prefixDirty) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                container.rfidSettingsStore.setEpcCompanyPrefix(prefixDraft)
                                container.rfidManager.applySettingsToReader()
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Save prefix")
                    }
                }
                if (diagnostics.epcPrefilterActive && diagnostics.epcPrefilterMask != null) {
                    Spacer(Modifier.height(4.dp))
                    ActiveChip("Active: ${diagnostics.epcPrefilterMask}")
                }
            }

            ErpCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsCardHeader(
                        icon = Icons.Default.Bluetooth,
                        title = "Reader diagnostics",
                        subtitle = if (diagnostics.connected) "Live from RFD90" else "Connect reader first",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { container.rfidManager.refreshDiagnostics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh diagnostics")
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConnectionStatusChip(connected = diagnostics.connected)
                Spacer(Modifier.height(12.dp))
                if (diagnostics.connected) {
                    Text(
                        "Scanner battery",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    ScannerBatteryMeter(
                        percent = diagnostics.batteryPercent,
                        charging = diagnostics.batteryCharging,
                        loading = diagnostics.batteryLoading,
                    )
                    Spacer(Modifier.height(12.dp))
                    ReaderSerialBlock(
                        serialNumber = diagnostics.serialNumber,
                        readerName = diagnostics.readerName,
                        showHelp = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    DiagnosticsGrid(diagnostics)
                } else {
                    Text(
                        "Pair the RFD90 on the Connect tab to see firmware, battery, and Bluetooth details here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                diagnostics.lastError?.let { error ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            error,
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            ErpCard {
                SettingsCardHeader(
                    icon = Icons.Default.SystemUpdate,
                    title = "Firmware update",
                    subtitle = "Check Zebra.com for RFD90 updates",
                )
                Spacer(Modifier.height(10.dp))
                SettingsToggleRow(
                    label = "Auto-check when reader connects",
                    checked = autoFirmwareCheck,
                    onCheckedChange = {
                        scope.launch { container.rfidSettingsStore.setAutoFirmwareCheck(it) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                diagnostics.firmwareVersion?.let {
                    Text(
                        "Installed: $it",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (firmwareUpdate.phase != FirmwareUpdateState.Phase.IDLE) {
                    Spacer(Modifier.height(10.dp))
                    FirmwareUpdateMeter(firmwareUpdate)
                }
                Spacer(Modifier.height(10.dp))
                ErpPrimaryButton(
                    text = when (firmwareUpdate.phase) {
                        FirmwareUpdateState.Phase.CHECKING -> "Checking…"
                        else -> "Check for firmware update"
                    },
                    enabled = isConnected && firmwareUpdate.phase != FirmwareUpdateState.Phase.CHECKING,
                    onClick = { container.rfidManager.checkFirmwareUpdate(refresh = true) },
                )
                if (firmwareUpdate.phase == FirmwareUpdateState.Phase.UPDATE_AVAILABLE) {
                    Spacer(Modifier.height(8.dp))
                    ErpPrimaryButton(
                        text = "Download from Zebra",
                        onClick = { container.rfidManager.downloadFirmwareUpdate() },
                    )
                }
                if (firmwareUpdate.phase == FirmwareUpdateState.Phase.READY_TO_INSTALL) {
                    Spacer(Modifier.height(8.dp))
                    ErpPrimaryButton(
                        text = "Install on reader",
                        onClick = { container.rfidManager.installFirmwareUpdate() },
                    )
                }
                if (firmwareUpdate.phase == FirmwareUpdateState.Phase.SUCCESS ||
                    firmwareUpdate.phase == FirmwareUpdateState.Phase.FAILED ||
                    firmwareUpdate.phase == FirmwareUpdateState.Phase.UP_TO_DATE
                ) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { container.rfidManager.clearFirmwareUpdateState() }) {
                        Text("Dismiss")
                    }
                }
            }

            InfoNoteCard(
                icon = Icons.Default.Info,
                title = "Firmware notes",
                body = "Updates are discovered from Zebra support pages via ERP, then downloaded and applied over Bluetooth. Keep battery above 20% and stay near the reader until it reboots. Encrypted .EDAT packages are not supported yet.",
            )

            SettingsSectionLabel("Server")

            ErpCard {
                OutlinedTextField(
                    value = apiUrlDraft,
                    onValueChange = { apiUrlDraft = it },
                    label = { Text("API base URL") },
                    supportingText = { Text("Default: ${BuildConfig.API_BASE_URL}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (apiUrlDraft.trimEnd('/') != apiBaseUrl.trimEnd('/')) {
                    Spacer(Modifier.height(8.dp))
                    ErpPrimaryButton(
                        text = "Save API URL",
                        onClick = {
                            scope.launch {
                                container.apiSettingsStore.setBaseUrl(apiUrlDraft)
                                message = "API URL saved — restart app if requests fail"
                            }
                        },
                    )
                }
            }

            SettingsSectionLabel("Sync")

            ErpCard {
                Text("Pending uploads: $pendingSync", style = MaterialTheme.typography.bodyMedium)
                if (failedSync.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${failedSync.size} batch(es) failed after ${com.erpcomplete.rfid.sync.SyncRepository.MAX_ATTEMPTS} tries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    failedSync.take(3).forEach { item ->
                        Text(
                            "${item.method} ${item.endpoint} — ${item.lastError ?: "error"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { container.syncRepository.flush() } },
                        modifier = Modifier.weight(1f),
                    ) { Text("Retry sync") }
                    OutlinedButton(
                        onClick = { scope.launch { container.syncRepository.discardFailed() } },
                        modifier = Modifier.weight(1f),
                        enabled = failedSync.isNotEmpty(),
                    ) { Text("Discard failed") }
                }
            }

            SettingsSectionLabel("Tags")

            ErpCard {
                OutlinedTextField(
                    value = voidEpc,
                    onValueChange = { voidEpc = it.uppercase() },
                    label = { Text("Void damaged tag (EPC)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                ErpPrimaryButton(
                    text = "Void tag",
                    onClick = {
                        scope.launch {
                            runCatching {
                                val epc = voidEpc.trim()
                                if (epc.isBlank()) error("Enter an EPC")
                                val res = container.api.voidTag(epc)
                                if (!res.isSuccessful) error(com.erpcomplete.rfid.util.ApiErrorParser.httpMessage(res, authenticated = true))
                                voidEpc = ""
                                "Tag voided"
                            }.onSuccess { message = it }.onFailure { message = it.message }
                        }
                    },
                )
            }

            SettingsSectionLabel("Account")

            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { container.api.logout() }
                        container.syncRepository.clearAll()
                        container.authStore.clear()
                        container.rfidManager.reset()
                        rootNavController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Sign out")
            }

            Text(
                "ERPComplete RFID ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                apiBaseUrl,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScanProfileOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Default.SignalCellularAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConnectionStatusChip(connected: Boolean) {
    val bg = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (connected) "Connected" else "Not connected"
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActiveChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DiagnosticsGrid(diagnostics: ReaderDiagnostics) {
    val rows = buildList {
        diagnostics.readerName?.let { add("Reader" to it) }
        diagnostics.modelName?.let { add("Model" to it) }
        diagnostics.firmwareVersion?.let { add("Firmware" to it) }
        diagnostics.serialNumber?.let { add("Serial" to it) }
        diagnostics.readerAddress?.let { add("Bluetooth" to it) }
        diagnostics.scanProfile?.let { add("Profile" to it) }
        diagnostics.antennaCount?.let { add("Antennas" to it.toString()) }
    }
    if (rows.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun InfoNoteCard(icon: ImageVector, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
