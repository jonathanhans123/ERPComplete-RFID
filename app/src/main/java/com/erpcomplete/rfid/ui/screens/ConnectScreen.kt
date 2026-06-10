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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.rfid.RfidConnectionState
import com.erpcomplete.rfid.ui.components.BluetoothDisabledBanner
import com.erpcomplete.rfid.ui.components.ErpCard
import com.erpcomplete.rfid.ui.components.ReaderStatusCard
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.ErpScaffold
import com.erpcomplete.rfid.ui.components.PairingCameraScanner
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
    var scanGeneration by remember { mutableIntStateOf(0) }

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

    val phoneMac = PhoneBluetooth.normalizeMac12(phoneMacInput)
    val pairingBarcode = remember(phoneMac) {
        phoneMac?.let { runCatching { BarcodeBitmap.code128(it, 900, 260) }.getOrNull() }
    }

    ErpScaffold(
        title = "RFID Reader",
        subtitle = "Zebra RFD90 Scan-to-Connect",
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BluetoothDisabledBanner()
            Spacer(Modifier.height(8.dp))
            val connectionState = state
            when {
                isConnected -> {
                    val name = (connectionState as? RfidConnectionState.Connected)?.readerName
                        ?: rfid.connectedName()
                        ?: "RFD90"
                    StatusBanner("Connected to $name")
                }
                connectionState is RfidConnectionState.Pairing -> StatusBanner("Pairing with reader…")
                connectionState is RfidConnectionState.Error -> StatusBanner(connectionState.message, isError = true)
                else -> StatusBanner("Pair using Scan-to-Connect — saved readers reconnect automatically.")
            }

            if (!isConnected) {
                Spacer(Modifier.height(12.dp))

                TabRow(selectedTabIndex = pairingTab) {
                    Tab(
                        selected = pairingTab == 0,
                        onClick = { pairingTab = 0 },
                        text = { Text("Scan reader QR") },
                    )
                    Tab(
                        selected = pairingTab == 1,
                        onClick = { pairingTab = 1 },
                        text = { Text("Show phone barcode") },
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
                        onShowManualMacField = { showManualMacField = true },
                        onPhoneMacChange = { value ->
                            phoneMacInput = value
                            macSource = PhoneMacInputSource.MANUAL
                        },
                        phoneMac = phoneMac,
                        pairingBarcode = pairingBarcode,
                        onRetryAutoDetect = {
                            scope.launch { refreshPhoneMac() }
                        },
                        onSaveMac = {
                            scope.launch {
                                PhoneBluetooth.normalizeMac12(phoneMacInput)?.let {
                                    rfid.readerStore().savePhoneMac(it)
                                    phoneMacInput = it
                                    macSource = PhoneMacInputSource.SAVED
                                    showManualMacField = false
                                    autoDetectFailed = false
                                }
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
                        Text("Hardware controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Top trigger — press and hold to start RFID scan; release to stop.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Bottom trigger — press to start continuous barcode scan; press again to stop.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (isConnected) {
                Spacer(Modifier.height(16.dp))
                ErpPrimaryButton(text = "Disconnect reader", onClick = { rfid.reset() })
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
                    "Scan the QR / barcode on your RFD90",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Recommended — no phone Bluetooth address needed. On the reader, open Scan-to-Connect and scan its barcode with this camera.",
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
    onShowManualMacField: () -> Unit,
    onPhoneMacChange: (String) -> Unit,
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
                "Let the RFD90 scan this phone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                needsBtPermission -> {
                    StatusBanner(
                        "Allow Nearby devices / Bluetooth permission so the app can read this phone's address automatically.",
                        isError = true,
                    )
                }
                macSource == PhoneMacInputSource.AUTO && phoneMac != null -> {
                    StatusBanner("Bluetooth address detected automatically.")
                    Text(
                        PhoneBluetooth.formatMac(phoneMac),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Show this barcode to the RFD90 Scan-to-Connect scanner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                macSource == PhoneMacInputSource.SAVED && phoneMac != null && !showManualMacField -> {
                    StatusBanner("Using saved Bluetooth address from a previous pairing.")
                    Text(
                        PhoneBluetooth.formatMac(phoneMac),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onRetryAutoDetect) {
                        Text("Try auto-detect again")
                    }
                }
                else -> {
                    Text(
                        if (autoDetectFailed) {
                            "Android blocks most apps from reading the phone Bluetooth address. " +
                                "Use the Scan reader QR tab when possible, or enter the address manually below."
                        } else {
                            "Detecting this phone's Bluetooth address…"
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
                            Text(if (showManualMacField) "Retry auto-detect" else "Enter address manually")
                        }
                    }
                }
            }

            if (showManualMacField || (autoDetectFailed && macSource == PhoneMacInputSource.MANUAL)) {
                OutlinedTextField(
                    value = phoneMacInput,
                    onValueChange = onPhoneMacChange,
                    label = { Text("Phone Bluetooth address") },
                    placeholder = { Text("A1B2C3D4E5F6") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                ErpPrimaryButton(text = "Save address", onClick = onSaveMac)
            }

            if (pairingBarcode != null && phoneMac != null) {
                Image(
                    bitmap = pairingBarcode.asImageBitmap(),
                    contentDescription = "Phone pairing barcode",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                ErpPrimaryButton(text = "Reader scanned — connect now", onClick = onReaderMayBePaired)
            } else if (!needsBtPermission && autoDetectFailed && !showManualMacField) {
                StatusBanner(
                    "Tip: open Connect → Scan reader QR — that flow never needs your phone address.",
                )
            }
        }
    }
}
