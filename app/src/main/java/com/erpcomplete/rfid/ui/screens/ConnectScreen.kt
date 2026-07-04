package com.erpcomplete.rfid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.rfid.RfidConnectionState
import com.erpcomplete.rfid.ui.components.BluetoothDisabledBanner
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ReaderStatusCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.MacAddressTextField
import com.erpcomplete.rfid.ui.components.PairingCameraScanner
import com.erpcomplete.rfid.ui.components.PairingScanMode
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.util.BarcodeBitmap
import com.erpcomplete.rfid.util.PhoneBluetooth
import kotlinx.coroutines.launch

private enum class PhoneMacInputSource {
    AUTO,
    SAVED,
    MANUAL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(container: AppContainer) {
    val state by container.rfidManager.connectionState.collectAsState()
    val isConnected by container.rfidManager.isDeviceConnected.collectAsState()
    val diagnostics by container.rfidManager.diagnostics.collectAsState()
    val rfid = container.rfidManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairingTab by remember { mutableIntStateOf(0) }
    var phoneMacInput by remember { mutableStateOf("") }
    var macSource by remember { mutableStateOf(PhoneMacInputSource.SAVED) }
    var autoDetectFailed by remember { mutableStateOf(false) }
    var showManualMacField by remember { mutableStateOf(false) }
    var showMacCameraScanner by remember { mutableStateOf(false) }
    var macScanError by remember { mutableStateOf<String?>(null) }
    var scanGeneration by remember { mutableIntStateOf(0) }

    fun applyPhoneMac(mac12: String) {
        phoneMacInput = mac12
        macSource = PhoneMacInputSource.MANUAL
        showManualMacField = true
        showMacCameraScanner = false
        macScanError = null
        scope.launch {
            rfid.readerStore().savePhoneMac(mac12)
            macSource = PhoneMacInputSource.SAVED
            autoDetectFailed = false
        }
    }

    LaunchedEffect(state) {
        if (state is RfidConnectionState.Error) {
            scanGeneration++
        }
    }

    val hasBtPermission = remember {
        PhoneBluetooth.hasBluetoothConnectPermission(context)
    }

    suspend fun refreshPhoneMac() {
        runCatching {
            if (!PhoneBluetooth.hasBluetoothConnectPermission(context)) {
                autoDetectFailed = true
                return
            }

            val detected = PhoneBluetooth.resolveLocalMac(context)
            if (detected != null) {
                phoneMacInput = detected.mac12
                macSource = PhoneMacInputSource.AUTO
                autoDetectFailed = false
                showManualMacField = false
                rfid.readerStore().savePhoneMac(detected.mac12)
                return
            }

            val saved = rfid.readerStore().getPhoneMac()
            if (!saved.isNullOrBlank() && macSource != PhoneMacInputSource.MANUAL) {
                phoneMacInput = saved
                macSource = PhoneMacInputSource.SAVED
                autoDetectFailed = true
                return
            }

            if (phoneMacInput.isBlank()) {
                autoDetectFailed = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = rfid.readerStore().getPhoneMac()
        if (!saved.isNullOrBlank()) {
            phoneMacInput = saved
            macSource = PhoneMacInputSource.SAVED
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) rfid.refreshDiagnostics()
    }

    LaunchedEffect(hasBtPermission, pairingTab) {
        if (pairingTab == 1) {
            refreshPhoneMac()
        }
    }

    val phoneMac = PhoneBluetooth.parseMacInput(phoneMacInput) ?: PhoneBluetooth.normalizeMac12(phoneMacInput)

    LaunchedEffect(autoDetectFailed, phoneMac, pairingTab) {
        if (pairingTab == 1 && autoDetectFailed && phoneMac == null) {
            showManualMacField = true
        }
    }

    val pairingBarcode = remember(phoneMac) {
        phoneMac?.let { runCatching { BarcodeBitmap.code128(it, 900, 260) }.getOrNull() }
    }

    ErpScaffold(
        title = stringResource(R.string.connect_title),
        subtitle = stringResource(R.string.connect_subtitle),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BluetoothDisabledBanner()
            Spacer(Modifier.height(8.dp))
            val connectionState = state
            when {
                isConnected -> {
                    val name = (connectionState as? RfidConnectionState.Connected)?.readerName
                        ?: rfid.connectedName()
                        ?: stringResource(R.string.connect_reader_default_name)
                    StatusBanner(stringResource(R.string.connect_status_connected, name))
                }
                connectionState is RfidConnectionState.Pairing -> StatusBanner(stringResource(R.string.connect_status_pairing))
                connectionState is RfidConnectionState.Error -> StatusBanner(connectionState.message, isError = true)
                else -> StatusBanner(stringResource(R.string.connect_status_idle))
            }

            if (!isConnected) {
                Spacer(Modifier.height(12.dp))

                TabRow(selectedTabIndex = pairingTab) {
                    Tab(
                        selected = pairingTab == 0,
                        onClick = { pairingTab = 0 },
                        text = { Text(stringResource(R.string.connect_tab_scan_reader)) },
                    )
                    Tab(
                        selected = pairingTab == 1,
                        onClick = { pairingTab = 1 },
                        text = { Text(stringResource(R.string.connect_tab_phone_barcode)) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                when (pairingTab) {
                    0 -> ScanReaderTab(
                        scanGeneration = scanGeneration,
                        isConnecting = connectionState is RfidConnectionState.Pairing,
                        onBarcodeScanned = { rfid.connectFromPairingBarcode(it) },
                    )
                    1 -> ShowPhoneBarcodeTab(
                        phoneMacInput = phoneMacInput,
                        macSource = macSource,
                        autoDetectFailed = autoDetectFailed,
                        showManualMacField = showManualMacField,
                        showMacCameraScanner = showMacCameraScanner,
                        macScanError = macScanError,
                        onShowManualMacField = { showManualMacField = true },
                        onToggleMacCameraScanner = { showMacCameraScanner = !showMacCameraScanner },
                        onPhoneMacChange = { value ->
                            phoneMacInput = value
                            macSource = PhoneMacInputSource.MANUAL
                            macScanError = null
                        },
                        onMacCaptured = { applyPhoneMac(it) },
                        phoneMac = phoneMac,
                        pairingBarcode = pairingBarcode,
                        onRetryAutoDetect = {
                            scope.launch { refreshPhoneMac() }
                        },
                        onSaveMac = {
                            PhoneBluetooth.parseMacInput(phoneMacInput)?.let { mac ->
                                applyPhoneMac(mac)
                            } ?: run {
                                macScanError = context.getString(R.string.connect_mac_invalid)
                            }
                        },
                        onReaderMayBePaired = { rfid.finishPhoneBarcodePairing() },
                    )
                }

                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(12.dp))
                if (diagnostics.connected) {
                    ReaderStatusCard(diagnostics = diagnostics)
                    Spacer(Modifier.height(12.dp))
                }
            }

            ErpCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    androidx.compose.material3.Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.connect_hardware_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.connect_hardware_top_trigger),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.connect_hardware_bottom_trigger),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (isConnected) {
                Spacer(Modifier.height(16.dp))
                ErpPrimaryButton(text = stringResource(R.string.connect_disconnect), onClick = { rfid.reset() })
            }
        }
    }
}

@Composable
private fun ScanReaderTab(
    scanGeneration: Int,
    isConnecting: Boolean,
    onBarcodeScanned: (String) -> Unit,
) {
    ErpCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.QrCode2, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.connect_scan_reader_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.connect_scan_reader_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PairingCameraScanner(
                scanGeneration = scanGeneration,
                isConnecting = isConnecting,
                onBarcodeScanned = onBarcodeScanned,
            )
        }
    }
}

@Composable
private fun ShowPhoneBarcodeTab(
    phoneMacInput: String,
    macSource: PhoneMacInputSource,
    autoDetectFailed: Boolean,
    showManualMacField: Boolean,
    showMacCameraScanner: Boolean,
    macScanError: String?,
    onShowManualMacField: () -> Unit,
    onToggleMacCameraScanner: () -> Unit,
    onPhoneMacChange: (String) -> Unit,
    onMacCaptured: (String) -> Unit,
    phoneMac: String?,
    pairingBarcode: android.graphics.Bitmap?,
    onRetryAutoDetect: () -> Unit,
    onSaveMac: () -> Unit,
    onReaderMayBePaired: () -> Unit,
) {
    val context = LocalContext.current
    val needsBtPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

    ErpCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.connect_phone_barcode_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                needsBtPermission -> {
                    StatusBanner(
                        stringResource(R.string.connect_bt_permission_required),
                        isError = true,
                    )
                }
                macSource == PhoneMacInputSource.AUTO && phoneMac != null -> {
                    StatusBanner(stringResource(R.string.connect_mac_auto_detected))
                    Text(
                        PhoneBluetooth.formatMac(phoneMac),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.connect_show_barcode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                macSource == PhoneMacInputSource.SAVED && phoneMac != null && !showManualMacField -> {
                    StatusBanner(stringResource(R.string.connect_mac_saved))
                    Text(
                        PhoneBluetooth.formatMac(phoneMac),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onRetryAutoDetect) {
                        Text(stringResource(R.string.connect_retry_auto_detect))
                    }
                }
                else -> {
                    Text(
                        if (autoDetectFailed) {
                            stringResource(R.string.connect_mac_auto_failed_body)
                        } else {
                            stringResource(R.string.connect_mac_detecting)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (autoDetectFailed) {
                        TextButton(
                            onClick = {
                                if (showManualMacField) onRetryAutoDetect() else onShowManualMacField()
                            },
                        ) {
                            Text(
                                if (showManualMacField) {
                                    stringResource(R.string.connect_retry_auto_detect_short)
                                } else {
                                    stringResource(R.string.connect_enter_mac_manually)
                                },
                            )
                        }
                    }
                }
            }

            if (showManualMacField || (autoDetectFailed && macSource == PhoneMacInputSource.MANUAL)) {
                MacAddressTextField(
                    value = phoneMacInput,
                    onValueChange = onPhoneMacChange,
                    label = { Text(stringResource(R.string.connect_phone_mac_label)) },
                    placeholder = { Text(stringResource(R.string.connect_phone_mac_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    onScanComplete = onMacCaptured,
                )
                TextButton(onClick = onToggleMacCameraScanner) {
                    Text(
                        if (showMacCameraScanner) {
                            stringResource(R.string.connect_hide_mac_camera)
                        } else {
                            stringResource(R.string.connect_scan_mac_with_camera)
                        },
                    )
                }
                if (showMacCameraScanner) {
                    PairingCameraScanner(
                        scanMode = PairingScanMode.MAC_ADDRESS,
                        onBarcodeScanned = {},
                        onMacCaptured = onMacCaptured,
                    )
                }
                if (!macScanError.isNullOrBlank()) {
                    StatusBanner(macScanError, isError = true)
                }
                ErpPrimaryButton(text = stringResource(R.string.connect_save_mac), onClick = onSaveMac)
            }

            if (pairingBarcode != null && phoneMac != null) {
                Image(
                    bitmap = pairingBarcode.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_phone_pairing_barcode),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                ErpPrimaryButton(text = stringResource(R.string.connect_reader_scanned_connect), onClick = onReaderMayBePaired)
            } else if (!needsBtPermission && autoDetectFailed && !showManualMacField) {
                StatusBanner(
                    stringResource(R.string.connect_tip_use_qr_tab),
                )
            }
        }
    }
}
