package com.erpcomplete.rfid.rfid

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.ReaderStore
import com.erpcomplete.rfid.data.RfidSettingsStore
import com.erpcomplete.rfid.data.ScanProfile
import com.erpcomplete.rfid.data.SavedReader
import com.erpcomplete.rfid.util.AppLog
import com.erpcomplete.rfid.util.PhoneBluetooth
import com.zebra.rfid.api3.DYNAMIC_POWER_OPTIMIZATION
import com.zebra.rfid.api3.ENUM_TRANSPORT
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE
import com.zebra.rfid.api3.HANDHELD_TRIGGER_TYPE
import com.zebra.rfid.api3.IEvents
import com.zebra.rfid.api3.RFModeTable
import com.zebra.rfid.api3.RFModeTableEntry
import com.zebra.rfid.api3.INVENTORY_STATE
import com.zebra.rfid.api3.InvalidUsageException
import com.zebra.rfid.api3.OperationFailureException
import com.zebra.rfid.api3.RFIDReader
import com.zebra.rfid.api3.RFIDResults
import com.zebra.rfid.api3.ReaderDevice
import com.zebra.rfid.api3.Readers
import com.zebra.rfid.api3.RfidEventsListener
import com.zebra.rfid.api3.RfidReadEvents
import com.zebra.rfid.api3.RfidStatusEvents
import com.zebra.rfid.api3.SESSION
import com.zebra.rfid.api3.SL_FLAG
import com.zebra.rfid.api3.START_TRIGGER_TYPE
import com.zebra.rfid.api3.STATUS_EVENT_TYPE
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE
import com.zebra.rfid.api3.TagData
import com.zebra.rfid.api3.TriggerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress

class RfidManager(
    private val appContext: Context,
    private val settingsStore: RfidSettingsStore,
    private val firmwareRepository: ZebraFirmwareRepository,
) {

    private val readerStore = ReaderStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connectionState = MutableStateFlow<RfidConnectionState>(RfidConnectionState.Idle)
    val connectionState: StateFlow<RfidConnectionState> = _connectionState.asStateFlow()

    private val _isDeviceConnected = MutableStateFlow(false)
    val isDeviceConnected: StateFlow<Boolean> = _isDeviceConnected.asStateFlow()

    private val _scanSession = MutableStateFlow(ScanSession.NONE)
    val scanSession: StateFlow<ScanSession> = _scanSession.asStateFlow()

    private val _scannedTags = MutableStateFlow<List<ScannedTag>>(emptyList())
    val scannedTags: StateFlow<List<ScannedTag>> = _scannedTags.asStateFlow()

    private val _searchScans = MutableStateFlow<List<SearchScanRow>>(emptyList())
    val searchScans: StateFlow<List<SearchScanRow>> = _searchScans.asStateFlow()

    private val _locateStrength = MutableStateFlow<Int?>(null)
    val locateStrength: StateFlow<Int?> = _locateStrength.asStateFlow()

    private val _locateScanning = MutableStateFlow(false)
    val locateScanning: StateFlow<Boolean> = _locateScanning.asStateFlow()

    private val _diagnostics = MutableStateFlow(ReaderDiagnostics())
    val diagnostics: StateFlow<ReaderDiagnostics> = _diagnostics.asStateFlow()

    private val _firmwareUpdate = MutableStateFlow(FirmwareUpdateState())
    val firmwareUpdate: StateFlow<FirmwareUpdateState> = _firmwareUpdate.asStateFlow()

    private var readers: Readers? = null
    private var reader: RFIDReader? = null
    private var connectedReaderDevice: ReaderDevice? = null
    private var eventHandler: RfidEventHandler? = null
    private var inventoryActive = false
    private var locationingActive = false
    private var triggerHeld = false
    private var locateTargetEpc: String? = null
    private var activePrefilterPattern: String? = null
    private var batteryPercent: Int? = null
    private var batteryHealth: Int? = null
    private var batteryCharging: Boolean? = null
    private var connectedReaderName: String? = null
    private var connectedReaderAddress: String? = null
    private val barcodeBridge = HandheldBarcodeBridge(appContext)
    private val inventoryMutex = Mutex()
    private var batteryPollJob: Job? = null

    init {
        barcodeBridge.initSdk()
    }

    fun setScanSession(session: ScanSession) {
        val previous = _scanSession.value
        if (previous == session) return
        _scanSession.value = session
        if (session == ScanSession.NONE) {
            triggerHeld = false
            stopInventory()
            stopLocate()
        } else if (previous == ScanSession.LOCATE) {
            triggerHeld = false
            stopLocate()
        } else if (previous == ScanSession.NONE) {
            inventoryActive = false
            locationingActive = false
            triggerHeld = false
        }
        reader?.takeIf { it.isConnected }?.let { applyEpcPrefilterIfNeeded(it) }
    }

    fun setLocateTarget(epc: String?) {
        locateTargetEpc = epc?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        if (locateTargetEpc == null) {
            _locateStrength.value = null
            stopLocate()
        }
        reader?.takeIf { it.isConnected }?.let { applyEpcPrefilterIfNeeded(it) }
        refreshDiagnostics()
    }

    /** @deprecated Use Settings → scan profile. Kept for locate screen chip during transition. */
    fun setLocateDenseMode(dense: Boolean) {
        scope.launch {
            settingsStore.setScanProfile(if (dense) ScanProfile.DENSE else ScanProfile.RANGE)
            applySettingsToReader()
        }
    }

    fun isLocateDenseMode(): Boolean = settingsStore.scanProfileBlocking() == ScanProfile.DENSE

    fun refreshDiagnostics() {
        scope.launch(Dispatchers.IO) {
            val r = reader
            if (r == null || !r.isConnected) {
                _diagnostics.value = ReaderDiagnostics(
                    connected = false,
                    lastError = (_connectionState.value as? RfidConnectionState.Error)?.message,
                )
                return@launch
            }
            refreshBatteryFromReader(r, publish = false)
            publishDiagnostics(r)
        }
    }

    fun applySettingsToReader() {
        scope.launch(Dispatchers.IO) {
            val r = reader?.takeIf { it.isConnected } ?: return@launch
            applyScanTuningConfig(r)
            applyEpcPrefilterIfNeeded(r)
            publishDiagnostics(r)
        }
    }

    suspend fun writeEpcToTag(sourceEpc: String, newEpc: String): TagWriteResult {
        return inventoryMutex.withLock {
            withContext(Dispatchers.IO) {
                val r = reader ?: return@withContext TagWriteResult.Failure(
                    appContext.getString(R.string.rfid_error_not_connected),
                )
                if (inventoryActive) stopInventoryInternal()
                if (locationingActive) stopLocationingInternal()
                RfidHardwareOps.writeEpc(appContext, r, sourceEpc, newEpc)
            }
        }
    }

    fun isLocateDiscovering(): Boolean =
        _scanSession.value == ScanSession.LOCATE && locateTargetEpc.isNullOrBlank()

    fun tryAutoReconnect() {
        scope.launch {
            if (_isDeviceConnected.value) return@launch
            val saved = readerStore.getSavedReader() ?: return@launch
            AppLog.i("Auto-reconnecting to ${saved.name}")
            _connectionState.value = RfidConnectionState.Pairing
            connectToAvailableReader(saved, attempts = 6)
        }
    }

    fun connectFromPairingBarcode(raw: String) {
        val parsed = PairingBarcodeParser.parse(raw)
        if (parsed == null) {
            AppLog.w("Unrecognized pairing barcode: ${raw.take(80)}")
            logConnectionError(appContext.getString(R.string.rfid_error_unrecognized_barcode))
            return
        }
        AppLog.i(
            "Pairing barcode parsed: mac=${parsed.macAddress} name=${parsed.readerName}",
        )
        scope.launch {
            _connectionState.value = RfidConnectionState.Pairing
            try {
                if (!PhoneBluetooth.isEnabled(appContext)) {
                    logConnectionError(appContext.getString(R.string.rfid_error_bluetooth_off_qr))
                    return@launch
                }
                val paired = withContext(Dispatchers.IO) {
                    BluetoothPairingHelper.pairIfNeeded(appContext, parsed)
                }
                if (!paired) {
                    logConnectionError(appContext.getString(R.string.rfid_error_pairing_failed_permissions))
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    runCatching { readers?.Dispose() }
                    readers = null
                }
                delay(2500)
                connectToAvailableReader(
                    saved = SavedReader(
                        name = parsed.readerName ?: parsed.macAddress ?: "RFD90",
                        address = parsed.macAddress ?: "",
                    ),
                    attempts = 10,
                )
            } catch (e: Exception) {
                logConnectionError(e.message ?: appContext.getString(R.string.rfid_error_pairing_failed))
            }
        }
    }

    fun finishPhoneBarcodePairing() {
        scope.launch {
            _connectionState.value = RfidConnectionState.Pairing
            delay(2000)
            val saved = readerStore.getSavedReader()
            connectToAvailableReader(saved, attempts = 10)
        }
    }

    private suspend fun connectToAvailableReader(saved: SavedReader?, attempts: Int) {
        withContext(Dispatchers.IO) {
            repeat(attempts) { attempt ->
                try {
                    val instance = readers ?: Readers(appContext, ENUM_TRANSPORT.BLUETOOTH).also { readers = it }
                    val list = instance.GetAvailableRFIDReaderList() ?: emptyList()
                    AppLog.d("RFID readers found: ${list.size} (attempt ${attempt + 1})")
                    val device = findBestDevice(list, saved)
                    if (device != null) {
                        connectDevice(device)
                        return@withContext
                    }
                } catch (e: Exception) {
                    AppLog.w("Reader discovery attempt failed: ${e.message}")
                }
                delay(1500)
            }
            logConnectionError(appContext.getString(R.string.rfid_error_reader_not_found))
        }
    }

    private fun findBestDevice(list: List<ReaderDevice>, saved: SavedReader?): ReaderDevice? {
        if (saved != null) {
            saved.address.takeIf { it.isNotBlank() }?.let { addr ->
                list.firstOrNull { it.address.equals(addr, ignoreCase = true) }?.let { return it }
            }
            list.firstOrNull { (it.name ?: "").contains(saved.name, ignoreCase = true) }?.let { return it }
        }
        return list.firstOrNull { (it.name ?: "").startsWith("RFD", ignoreCase = true) }
            ?: list.firstOrNull()
    }

    private suspend fun connectDevice(device: ReaderDevice) {
        withContext(Dispatchers.IO) {
            val r = try {
                device.getRFIDReader().also { readerInstance ->
                    if (!readerInstance.isConnected) {
                        readerInstance.connect()
                    }
                }
            } catch (e: InvalidUsageException) {
                logConnectionError(appContext.getString(R.string.rfid_error_invalid_usage, e.message ?: ""))
                return@withContext
            } catch (e: OperationFailureException) {
                logConnectionError(
                    appContext.getString(R.string.rfid_error_connect_failed, e.vendorMessage ?: ""),
                )
                return@withContext
            }

            reader = r
            connectedReaderDevice = device
            val name = device.name ?: r.hostName
            connectedReaderName = name
            connectedReaderAddress = device.address

            runCatching { configureReader(r) }.onFailure { e ->
                AppLog.w("Reader configure warning: ${e.message}")
            }
            runCatching { readerStore.saveReader(name, device.address) }.onFailure { e ->
                AppLog.w("Save reader failed: ${e.message}")
            }

            _isDeviceConnected.value = true
            _connectionState.value = RfidConnectionState.Connected(name)
            AppLog.i("Connected to $name (${device.address})")

            runCatching {
                barcodeBridge.attach(name, device.address, ::onHardwareBarcodeReceived)
            }.onFailure { e ->
                AppLog.w("Barcode bridge attach failed: ${e.message}")
            }
            runCatching { applyEpcPrefilterIfNeeded(r) }.onFailure { e ->
                AppLog.w("EPC pre-filter skipped: ${e.message}")
            }
            startBatteryMonitoring()
            publishDiagnostics(r)
            maybeAutoCheckFirmwareUpdate()
        }
    }

    fun checkFirmwareUpdate(refresh: Boolean = false) {
        scope.launch {
            val r = reader
            if (r == null || !r.isConnected) {
                _firmwareUpdate.value = FirmwareUpdateState(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    message = appContext.getString(R.string.rfid_firmware_connect_first),
                )
                return@launch
            }
            val model = ZebraFirmwareVersion.resolveReaderModel(
                RfidHardwareOps.readModelName(r),
                connectedReaderName,
            )
            if (model == null) {
                _firmwareUpdate.value = FirmwareUpdateState(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    message = appContext.getString(R.string.rfid_firmware_unsupported_model),
                )
                return@launch
            }
            val current = ZebraFirmwareVersion.normalize(RfidHardwareOps.readFirmwareVersion(r))
            _firmwareUpdate.value = FirmwareUpdateState(
                phase = FirmwareUpdateState.Phase.CHECKING,
                currentVersion = current,
                message = appContext.getString(R.string.rfid_firmware_checking_catalog),
            )
            runCatching {
                val result = firmwareRepository.checkForUpdate(model, current, refresh)
                settingsStore.markFirmwareCheckedNow()
                when {
                    !result.update_available && result.latest_version == null -> FirmwareUpdateState(
                        phase = FirmwareUpdateState.Phase.UP_TO_DATE,
                        currentVersion = current,
                        availableVersion = result.latest_version,
                        message = result.catalog_message
                            ?: appContext.getString(R.string.rfid_firmware_catalog_unavailable),
                    )
                    !result.update_available -> FirmwareUpdateState(
                        phase = FirmwareUpdateState.Phase.UP_TO_DATE,
                        currentVersion = current,
                        availableVersion = result.latest_version,
                        message = appContext.getString(R.string.rfid_firmware_up_to_date),
                    )
                    else -> FirmwareUpdateState(
                        phase = FirmwareUpdateState.Phase.UPDATE_AVAILABLE,
                        currentVersion = current,
                        availableVersion = result.latest_version,
                        downloadUrl = result.download_url,
                        fileName = result.file_name,
                        message = appContext.getString(
                            R.string.rfid_firmware_available,
                            result.latest_version,
                        ),
                    )
                }
            }.onSuccess {
                _firmwareUpdate.value = it
            }.onFailure {
                _firmwareUpdate.value = FirmwareUpdateState(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    currentVersion = current,
                    message = it.message ?: appContext.getString(R.string.rfid_firmware_check_failed),
                )
            }
        }
    }

    fun downloadFirmwareUpdate() {
        scope.launch {
            val state = _firmwareUpdate.value
            val url = state.downloadUrl
            val fileName = state.fileName?.takeIf { it.isNotBlank() } ?: "reader-firmware.dat"
            if (url.isNullOrBlank()) {
                _firmwareUpdate.value = state.copy(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    message = appContext.getString(R.string.rfid_firmware_no_download_url),
                )
                return@launch
            }
            val target = File(appContext.cacheDir, "firmware/${fileName.lowercase()}")
            _firmwareUpdate.value = state.copy(
                phase = FirmwareUpdateState.Phase.DOWNLOADING,
                progressPercent = 0,
                message = appContext.getString(R.string.rfid_firmware_downloading),
            )
            runCatching {
                firmwareRepository.downloadFirmware(url, target) { progress ->
                    _firmwareUpdate.value = _firmwareUpdate.value.copy(
                        progressPercent = progress,
                    )
                }
            }.onSuccess { file ->
                _firmwareUpdate.value = _firmwareUpdate.value.copy(
                    phase = FirmwareUpdateState.Phase.READY_TO_INSTALL,
                    progressPercent = 100,
                    localFilePath = file.absolutePath,
                    message = appContext.getString(R.string.rfid_firmware_downloaded_ready),
                )
            }.onFailure {
                _firmwareUpdate.value = _firmwareUpdate.value.copy(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    message = it.message ?: appContext.getString(R.string.rfid_firmware_download_failed),
                )
            }
        }
    }

    fun installFirmwareUpdate() {
        scope.launch {
            val state = _firmwareUpdate.value
            val path = state.localFilePath
            val r = reader
            if (path.isNullOrBlank() || r == null || !r.isConnected) {
                _firmwareUpdate.value = state.copy(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    message = appContext.getString(R.string.rfid_firmware_connect_and_download_first),
                )
                return@launch
            }
            val battery = batteryPercent
            if (battery != null && battery < 20) {
                _firmwareUpdate.value = state.copy(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    message = appContext.getString(R.string.rfid_firmware_battery_low),
                )
                return@launch
            }
            if (inventoryActive) {
                stopInventoryInternal()
            }
            _firmwareUpdate.value = state.copy(
                phase = FirmwareUpdateState.Phase.INSTALLING,
                progressPercent = 0,
                message = appContext.getString(R.string.rfid_firmware_installing),
            )
            withContext(Dispatchers.IO) {
                runCatching {
                    val outcome = r.Config.updateFirmware(path, localHostAddress())
                    if (outcome != RFIDResults.RFID_API_SUCCESS) {
                        error(appContext.getString(R.string.rfid_firmware_rejected, outcome.toString()))
                    }
                }.onSuccess {
                    _firmwareUpdate.value = _firmwareUpdate.value.copy(
                        phase = FirmwareUpdateState.Phase.INSTALLING,
                        message = appContext.getString(R.string.rfid_firmware_transfer_started),
                    )
                }.onFailure {
                    _firmwareUpdate.value = _firmwareUpdate.value.copy(
                        phase = FirmwareUpdateState.Phase.FAILED,
                        message = it.message ?: appContext.getString(R.string.rfid_firmware_install_failed),
                    )
                }
            }
        }
    }

    fun clearFirmwareUpdateState() {
        _firmwareUpdate.value = FirmwareUpdateState()
    }

    private fun maybeAutoCheckFirmwareUpdate() {
        if (!settingsStore.autoFirmwareCheckBlocking()) return
        scope.launch {
            val lastCheck = settingsStore.lastFirmwareCheckAt()
            val stale = lastCheck == null || System.currentTimeMillis() - lastCheck > AUTO_FIRMWARE_CHECK_INTERVAL_MS
            if (!stale) return@launch
            if (_firmwareUpdate.value.phase == FirmwareUpdateState.Phase.CHECKING) return@launch
            checkFirmwareUpdate(refresh = false)
        }
    }

    private fun localHostAddress(): String {
        return runCatching {
            val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                Formatter.formatIpAddress(ip)
            } else {
                InetAddress.getLocalHost().hostAddress
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
    }

    private fun configureReader(r: RFIDReader) {
        if (!r.isConnected) return

        if (isRfdHandheldReader(r)) {
            runCatching {
                r.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, false)
                AppLog.i("RFD trigger mode set to RFID (top trigger = inventory)")
            }.onFailure { e -> AppLog.w("setTriggerMode skipped: ${e.message}") }
        }

        val triggerInfo = TriggerInfo().apply {
            StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE
            StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE
        }

        eventHandler?.let { old ->
            runCatching { r.Events.removeEventsListener(old) }
        }
        eventHandler = RfidEventHandler()
        r.Events.addEventsListener(eventHandler)
        r.Events.setHandheldEvent(true)
        r.Events.setTagReadEvent(true)
        runCatching { r.Events.setBatteryEvent(true) }
            .onFailure { e -> AppLog.w("Battery events not supported: ${e.message}") }
        runCatching { r.Events.setFirmwareUpdateEvent(true) }
            .onFailure { e -> AppLog.w("Firmware update events not supported: ${e.message}") }
        runCatching { r.PostConnectReaderUpdate() }
            .onFailure { e -> AppLog.w("PostConnectReaderUpdate skipped: ${e.message}") }
        refreshBatteryFromReader(r, publish = false)
        r.Events.setAttachTagDataWithReadEvent(false)
        r.Events.setReaderDisconnectEvent(true)
        runCatching { r.Config.setStartTrigger(triggerInfo.StartTrigger) }
            .onFailure { e -> AppLog.w("Start trigger skipped: ${e.message}") }
        runCatching { r.Config.setStopTrigger(triggerInfo.StopTrigger) }
            .onFailure { e -> AppLog.w("Stop trigger skipped: ${e.message}") }
        applyScanTuningConfig(r)
    }

    private fun isRfdHandheldReader(r: RFIDReader): Boolean {
        val host = r.hostName?.uppercase().orEmpty()
        return host.startsWith("RFD90") ||
            host.startsWith("RFD40") ||
            host.startsWith("RFD8500")
    }

    fun startInventory() {
        if (_scanSession.value == ScanSession.NONE) return
        scope.launch { startInventoryInternal() }
    }

    fun stopInventory() {
        scope.launch { stopInventoryInternal() }
    }

    private suspend fun startInventoryInternal() {
        val session = _scanSession.value
        if (session == ScanSession.NONE) return
        if (session == ScanSession.LOCATE && !locateTargetEpc.isNullOrBlank()) return
        inventoryMutex.withLock {
            val r = reader ?: run {
                logOperationalError("Not connected")
                return
            }
            withContext(Dispatchers.IO) {
                try {
                    if (!inventoryActive) {
                        runCatching { r.Actions.purgeTags() }
                        r.Actions.Inventory.perform()
                        inventoryActive = true
                        AppLog.d("RFID inventory started (${_scanSession.value})")
                    }
                } catch (e: Exception) {
                    inventoryActive = false
                    logOperationalError(e.message ?: "Inventory start failed")
                }
            }
        }
    }

    private suspend fun stopInventoryInternal() {
        inventoryMutex.withLock {
            val r = reader ?: return
            withContext(Dispatchers.IO) {
                try {
                    r.Actions.Inventory.stop()
                    AppLog.d("RFID inventory stopped")
                } catch (e: Exception) {
                    Log.w(TAG, "Stop inventory: ${e.message}")
                } finally {
                    inventoryActive = false
                }
            }
        }
    }

    private suspend fun startLocationingInternal() {
        if (_scanSession.value != ScanSession.LOCATE) return
        val epc = locateTargetEpc ?: run {
            logOperationalError("Enter an EPC to locate")
            return
        }
        inventoryMutex.withLock {
            val r = reader ?: run {
                logOperationalError("Not connected")
                return
            }
            withContext(Dispatchers.IO) {
                try {
                    if (!locationingActive) {
                        runCatching { r.Actions.purgeTags() }
                        r.Actions.TagLocationing.Perform(epc, null, null)
                        locationingActive = true
                        _locateScanning.value = true
                        AppLog.d("Tag locationing started for $epc")
                    }
                } catch (e: Exception) {
                    locationingActive = false
                    _locateScanning.value = false
                    logOperationalError(e.message ?: "Locate failed")
                }
            }
        }
    }

    private suspend fun stopLocationingInternal() {
        inventoryMutex.withLock {
            val r = reader
            withContext(Dispatchers.IO) {
                try {
                    r?.takeIf { it.isConnected }?.Actions?.TagLocationing?.Stop()
                    AppLog.d("Tag locationing stopped")
                } catch (e: Exception) {
                    Log.w(TAG, "Stop locationing: ${e.message}")
                } finally {
                    locationingActive = false
                    _locateScanning.value = false
                    _locateStrength.value = null
                }
            }
        }
    }

    fun clearScannedTags() {
        _scannedTags.value = emptyList()
    }

    fun removeScannedTag(code: String) {
        val normalized = code.trim().uppercase()
        _scannedTags.value = _scannedTags.value.filter { !it.epc.equals(normalized, ignoreCase = true) }
    }

    fun clearSearchScans() {
        _searchScans.value = emptyList()
    }

    fun recordBarcode(code: String) {
        if (_scanSession.value != ScanSession.SEARCH) return
        val trimmed = code.trim()
        if (trimmed.isBlank()) return
        addSearchScan(trimmed, ScanType.BARCODE)
    }

    fun recordWorkflowBarcode(code: String) {
        if (_scanSession.value != ScanSession.WORK && _scanSession.value != ScanSession.ENCODE) return
        val trimmed = code.trim()
        if (trimmed.isBlank()) return
        addWorkflowScan(trimmed.uppercase(), ScanType.BARCODE)
    }

    private fun onHardwareBarcodeReceived(code: String) {
        when (_scanSession.value) {
            ScanSession.SEARCH -> recordBarcode(code)
            ScanSession.WORK -> recordWorkflowBarcode(code)
            ScanSession.LOCATE, ScanSession.NONE -> Unit
            ScanSession.ENCODE -> recordWorkflowBarcode(code)
        }
    }

    fun stopLocate() {
        scope.launch { stopLocationingInternal() }
    }

    fun reset() {
        stopInventory()
        stopLocate()
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    reader?.takeIf { it.isConnected }?.disconnect()
                } catch (_: Exception) {
                }
                reader = null
                connectedReaderDevice = null
                try {
                    readers?.Dispose()
                } catch (_: Exception) {
                }
                readers = null
            }
            barcodeBridge.detach()
            readerStore.clear()
            connectedReaderName = null
            connectedReaderAddress = null
            _isDeviceConnected.value = false
            _scannedTags.value = emptyList()
            _searchScans.value = emptyList()
            _locateStrength.value = null
            _locateScanning.value = false
            locateTargetEpc = null
            activePrefilterPattern = null
            stopBatteryMonitoring()
            batteryPercent = null
            batteryHealth = null
            batteryCharging = null
            locationingActive = false
            _diagnostics.value = ReaderDiagnostics()
            _connectionState.value = RfidConnectionState.Idle
        }
    }

    fun readerStore(): ReaderStore = readerStore

    fun connectedName(): String? = connectedReaderName

    private fun onTagsRead(tags: Array<TagData>) {
        when (_scanSession.value) {
            ScanSession.NONE -> return
            ScanSession.LOCATE -> {
                if (!locateTargetEpc.isNullOrBlank()) return
                val now = System.currentTimeMillis()
                for (tag in tags) {
                    val epc = tag.tagID?.uppercase() ?: continue
                    addWorkflowScan(epc, ScanType.RFID, tag.peakRSSI, now)
                }
            }
            ScanSession.SEARCH -> {
                for (tag in tags) {
                    val epc = tag.tagID?.uppercase() ?: continue
                    addSearchScan(epc, ScanType.RFID)
                }
            }
            ScanSession.WORK, ScanSession.ENCODE -> {
                val now = System.currentTimeMillis()
                for (tag in tags) {
                    val epc = tag.tagID?.uppercase() ?: continue
                    addWorkflowScan(epc, ScanType.RFID, tag.peakRSSI, now)
                }
            }
        }
    }

    private fun addWorkflowScan(
        code: String,
        type: ScanType,
        rssi: Short = 0,
        lastSeenAt: Long = System.currentTimeMillis(),
    ) {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return
        val current = _scannedTags.value.toMutableList()
        val existing = current.indexOfFirst { it.epc.equals(normalized, ignoreCase = true) }
        if (existing >= 0) {
            current[existing] = current[existing].copy(rssi = rssi, lastSeenAt = lastSeenAt, type = type)
        } else {
            current.add(0, ScannedTag(normalized, rssi, lastSeenAt, type))
        }
        _scannedTags.value = current.sortedByDescending { it.lastSeenAt }
    }

    private fun addSearchScan(code: String, type: ScanType) {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return
        if (_searchScans.value.any { it.code.equals(normalized, ignoreCase = true) }) return

        val now = System.currentTimeMillis()
        val current = _searchScans.value.toMutableList()
        current.add(0, SearchScanRow(normalized, type, now))
        _searchScans.value = current
    }

    private suspend fun handleHandheldTrigger(pressed: Boolean) {
        when (_scanSession.value) {
            ScanSession.NONE -> return
            ScanSession.LOCATE -> {
                if (pressed) {
                    if (triggerHeld) return
                    triggerHeld = true
                    if (locateTargetEpc.isNullOrBlank()) {
                        if (locationingActive) stopLocationingInternal()
                        startInventoryInternal()
                    } else {
                        if (inventoryActive) stopInventoryInternal()
                        startLocationingInternal()
                    }
                } else {
                    triggerHeld = false
                    if (locateTargetEpc.isNullOrBlank()) {
                        stopInventoryInternal()
                    } else {
                        stopLocationingInternal()
                    }
                }
            }
            else -> {
                if (pressed) {
                    if (triggerHeld) return
                    triggerHeld = true
                    if (inventoryActive) stopInventoryInternal()
                    startInventoryInternal()
                } else {
                    triggerHeld = false
                    stopInventoryInternal()
                }
            }
        }
    }

    private fun logConnectionError(message: String) {
        Log.e(TAG, message)
        barcodeBridge.detach()
        _isDeviceConnected.value = false
        connectedReaderName = null
        connectedReaderAddress = null
        _connectionState.value = RfidConnectionState.Error(message)
    }

    private fun logOperationalError(message: String) {
        Log.w(TAG, message)
    }

    private fun applyScanTuningConfig(r: RFIDReader) {
        val powerLevels = r.ReaderCapabilities.transmitPowerLevelValues
        val maxPowerIndex = (powerLevels?.size ?: 1) - 1
        val dense = settingsStore.scanProfileBlocking() == ScanProfile.DENSE
        val powerIndex = if (dense) (maxPowerIndex * 0.45).toInt().coerceAtLeast(0) else maxPowerIndex
        val rfModeIndex = if (dense) selectDenseRfModeIndex(r) else selectMaxRangeRfModeIndex(r)
        val antennaCount = r.ReaderCapabilities.numAntennaSupported.coerceAtLeast(1)

        for (antennaId in 1..antennaCount) {
            runCatching {
                val config = r.Config.Antennas.getAntennaRfConfig(antennaId)
                config.transmitPowerIndex = powerIndex.coerceIn(0, maxPowerIndex)
                config.setrfModeTableIndex(rfModeIndex.toLong())
                config.tari = 0
                r.Config.Antennas.setAntennaRfConfig(antennaId, config)
            }.onFailure { e ->
                AppLog.w("Antenna RF config skipped (antenna $antennaId): ${e.message}")
            }

            runCatching {
                val singulation = r.Config.Antennas.getSingulationControl(antennaId)
                singulation.session = SESSION.SESSION_S2
                singulation.Action.inventoryState = INVENTORY_STATE.INVENTORY_STATE_AB_FLIP
                singulation.Action.slFlag = SL_FLAG.SL_ALL
                r.Config.Antennas.setSingulationControl(antennaId, singulation)
            }.onFailure { e ->
                AppLog.w("Singulation config skipped (antenna $antennaId): ${e.message}")
            }
        }

        runCatching { r.Config.setDPOState(DYNAMIC_POWER_OPTIMIZATION.DISABLE) }
            .onFailure { e -> AppLog.w("DPO config skipped: ${e.message}") }
        runCatching { r.Config.setUniqueTagReport(true) }
            .onFailure { e -> AppLog.w("Unique tag report skipped: ${e.message}") }

        val powerDbm = powerLevels?.getOrNull(powerIndex)
        val profile = if (dense) "dense" else "range"
        AppLog.i(
            "RFID tuned ($profile): powerIndex=$powerIndex (${powerDbm ?: "?"} dBm), " +
                "rfModeTableIndex=$rfModeIndex, session=S2, AB-flip",
        )
    }

    /** Faster link / higher BDR profiles reduce cross-reads in dense tag fields. */
    private fun selectDenseRfModeIndex(r: RFIDReader): Int {
        val rfModes: RFModeTable = try {
            r.ReaderCapabilities.RFModes.getRFModeTableInfo(0)
        } catch (_: Exception) {
            return 0
        }
        var bestTableIndex = 0
        var bestScore = Int.MIN_VALUE
        for (i in 0 until rfModes.length()) {
            val entry: RFModeTableEntry = rfModes.getRFModeTableEntryInfo(i)
            val miller = when (entry.modulation.value) {
                1 -> 2
                2 -> 4
                3 -> 8
                else -> 1
            }
            val score = entry.bdrValue * miller
            if (score > bestScore) {
                bestScore = score
                bestTableIndex = i
            }
        }
        return bestTableIndex
    }

    /** Lower link rate / FM0 profiles improve read range; S2 + AB-flip still helps dense tag fields. */
    private fun selectMaxRangeRfModeIndex(r: RFIDReader): Int {
        val rfModes: RFModeTable = try {
            r.ReaderCapabilities.RFModes.getRFModeTableInfo(0)
        } catch (_: Exception) {
            return 0
        }
        var bestTableIndex = 0
        var bestScore = Int.MAX_VALUE
        for (i in 0 until rfModes.length()) {
            val entry: RFModeTableEntry = rfModes.getRFModeTableEntryInfo(i)
            val miller = when (entry.modulation.value) {
                1 -> 2
                2 -> 4
                3 -> 8
                else -> 1
            }
            val fm0Bonus = if (miller == 1) 0 else 50_000
            val score = entry.bdrValue * miller + fm0Bonus
            if (score < bestScore) {
                bestScore = score
                bestTableIndex = i
            }
        }
        return bestTableIndex
    }

    private fun resolveEpcPrefilterPattern(): String? {
        if (!settingsStore.epcPrefilterEnabledBlocking()) return null
        if (_scanSession.value == ScanSession.ENCODE) return null
        locateTargetEpc?.let { target ->
            return RfidHardwareOps.normalizeHexPattern(target)
        }
        return RfidHardwareOps.normalizeHexPattern(settingsStore.epcCompanyPrefixBlocking())
    }

    private fun applyEpcPrefilterIfNeeded(r: RFIDReader) {
        if (!r.isConnected) return
        val pattern = resolveEpcPrefilterPattern()
        activePrefilterPattern = pattern
        runCatching {
            if (pattern != null) {
                RfidHardwareOps.applyEpcPrefilter(r, pattern)
            } else {
                RfidHardwareOps.clearEpcPrefilter(r)
            }
        }.onFailure { e ->
            activePrefilterPattern = null
            AppLog.w("EPC pre-filter skipped: ${e.message}")
        }
    }

    private fun startBatteryMonitoring() {
        stopBatteryMonitoring()
        batteryPollJob = scope.launch(Dispatchers.IO) {
            for (delayMs in BATTERY_CONNECT_RETRY_DELAYS_MS) {
                if (delayMs > 0) delay(delayMs)
                val r = reader?.takeIf { it.isConnected } ?: return@launch
                refreshBatteryFromReader(r, publish = true)
                if (batteryPercent != null) break
            }
            while (isActive) {
                delay(BATTERY_POLL_INTERVAL_MS)
                val r = reader?.takeIf { it.isConnected } ?: break
                refreshBatteryFromReader(r, publish = true)
            }
        }
    }

    private fun stopBatteryMonitoring() {
        batteryPollJob?.cancel()
        batteryPollJob = null
    }

    private fun refreshBatteryFromReader(r: RFIDReader, publish: Boolean) {
        RfidHardwareOps.pollBatteryStatus(r)
        mergeBatteryReading(RfidHardwareOps.readBatteryStatsSync(r))
        batteryHealth = runCatching { r.Config.getBatteryHealth() }.getOrNull()
        if (publish) publishDiagnostics(r)
    }

    private fun mergeBatteryReading(reading: RfidHardwareOps.BatteryReading) {
        if (reading.percent != null) batteryPercent = reading.percent
        if (reading.charging != null) batteryCharging = reading.charging
    }

    private fun applyBatteryEvent(battery: IEvents.BatteryData) {
        val level = runCatching { battery.getLevel() }.getOrNull()
        val chargingRaw = runCatching { battery.getCharging() }.getOrNull()
        mergeBatteryReading(RfidHardwareOps.batteryFromEvent(level, chargingRaw))
    }

    private fun publishDiagnostics(r: RFIDReader) {
        val profile = settingsStore.scanProfileBlocking()
        batteryHealth = batteryHealth ?: runCatching { r.Config.getBatteryHealth() }.getOrNull()
        val loading = r.isConnected && batteryPercent == null && batteryPollJob?.isActive == true
        _diagnostics.value = ReaderDiagnostics(
            connected = r.isConnected,
            readerName = connectedReaderName,
            readerAddress = connectedReaderAddress,
            modelName = RfidHardwareOps.readModelName(r),
            serialNumber = RfidHardwareOps.resolveSerialNumber(r, connectedReaderName),
            firmwareVersion = RfidHardwareOps.readFirmwareVersion(r),
            antennaCount = runCatching { r.ReaderCapabilities.numAntennaSupported }.getOrNull(),
            batteryPercent = batteryPercent,
            batteryHealth = batteryHealth,
            batteryCharging = batteryCharging,
            batteryLoading = loading,
            scanProfile = if (profile == ScanProfile.DENSE) "dense" else "range",
            epcPrefilterActive = activePrefilterPattern != null,
            epcPrefilterMask = activePrefilterPattern,
        )
    }

    private fun isRfidTriggerEvent(triggerType: HANDHELD_TRIGGER_TYPE): Boolean {
        return triggerType == HANDHELD_TRIGGER_TYPE.HANDHELD_TRIGGER_RFID ||
            triggerType == HANDHELD_TRIGGER_TYPE.HANDHELD_TRIGGER_DUAL
    }

    private fun drainTagReads(r: RFIDReader) {
        while (true) {
            val tags = r.Actions.getReadTags(READ_BATCH_SIZE) ?: break
            if (tags.isEmpty()) break
            onTagsRead(tags)
        }
    }

    private fun drainLocationReads(r: RFIDReader) {
        while (true) {
            val tags = r.Actions.getReadTags(READ_BATCH_SIZE) ?: break
            if (tags.isEmpty()) break
            for (tag in tags) {
                if (!tag.isContainsLocationInfo) continue
                val distance = tag.LocationInfo.relativeDistance.toInt()
                _locateStrength.value = distance.coerceIn(0, 100)
            }
        }
    }

    private fun handleFirmwareUpdateEvent(fwEventData: Any?) {
        val fw = fwEventData ?: return
        val statusText = runCatching { fw.javaClass.getMethod("getStatus").invoke(fw) as? String }.getOrNull()
            ?: runCatching { fw.javaClass.getField("m_status").get(fw) as? String }.getOrNull()
            ?: return
        val progress = runCatching { fw.javaClass.getMethod("getImageDownloadProgress").invoke(fw) as? Int }.getOrNull()
            ?: 0
        val current = _firmwareUpdate.value
        when {
            statusText.startsWith("Error:") -> {
                _firmwareUpdate.value = current.copy(
                    phase = FirmwareUpdateState.Phase.FAILED,
                    progressPercent = progress,
                    message = statusText.removePrefix("Error:").trim().ifBlank {
                        appContext.getString(R.string.rfid_firmware_update_failed)
                    },
                )
            }
            statusText == "FWUpdate_END" && progress >= 100 -> {
                _firmwareUpdate.value = current.copy(
                    phase = FirmwareUpdateState.Phase.SUCCESS,
                    progressPercent = 100,
                    message = appContext.getString(R.string.rfid_firmware_updated_reconnect),
                )
                reader?.takeIf { it.isConnected }?.let { publishDiagnostics(it) }
            }
            else -> {
                _firmwareUpdate.value = current.copy(
                    phase = FirmwareUpdateState.Phase.INSTALLING,
                    progressPercent = progress.coerceIn(0, 100),
                    message = appContext.getString(R.string.rfid_firmware_installing_progress, progress),
                )
            }
        }
    }

    private inner class RfidEventHandler : RfidEventsListener {
        override fun eventReadNotify(events: RfidReadEvents?) {
            val r = reader ?: return
            when (_scanSession.value) {
                ScanSession.NONE -> return
                ScanSession.LOCATE -> scope.launch(Dispatchers.IO) {
                    try {
                        if (locateTargetEpc.isNullOrBlank()) {
                            drainTagReads(r)
                        } else {
                            drainLocationReads(r)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Locate read notify error: ${e.message}")
                    }
                }
                else -> scope.launch(Dispatchers.IO) {
                    try {
                        drainTagReads(r)
                    } catch (e: Exception) {
                        Log.w(TAG, "Read notify error: ${e.message}")
                    }
                }
            }
        }

        override fun eventStatusNotify(events: RfidStatusEvents?) {
            val status = events?.StatusEventData ?: return
            if (status.statusEventType == STATUS_EVENT_TYPE.BATTERY_EVENT) {
                val battery = status.BatteryData ?: return
                applyBatteryEvent(battery)
                reader?.takeIf { it.isConnected }?.let { publishDiagnostics(it) }
                return
            }
            if (status.statusEventType == STATUS_EVENT_TYPE.FIRMWARE_UPDATE_EVENT) {
                handleFirmwareUpdateEvent(status.FWEventData)
                return
            }
            if (status.statusEventType != STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) return

            val triggerData = status.HandheldTriggerEventData
            val triggerType = triggerData.handheldTriggerType
            if (triggerType == HANDHELD_TRIGGER_TYPE.HANDHELD_TRIGGER_SCAN) {
                AppLog.d("Barcode trigger event (${triggerData.handheldEvent}) — decode via barcode SDK")
                return
            }
            if (!isRfidTriggerEvent(triggerType)) {
                AppLog.d("Ignoring non-RFID trigger event: $triggerType")
                return
            }

            when (triggerData.handheldEvent) {
                HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED -> {
                    AppLog.d("RFID trigger pressed ($triggerType)")
                    scope.launch(Dispatchers.IO) { handleHandheldTrigger(true) }
                }
                HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED,
                HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_UNLOCK,
                -> {
                    AppLog.d("RFID trigger released ($triggerType)")
                    scope.launch(Dispatchers.IO) { handleHandheldTrigger(false) }
                }
                else -> Unit
            }
        }
    }

    enum class ScanType { RFID, BARCODE }

    data class ScannedTag(
        val epc: String,
        val rssi: Short,
        val lastSeenAt: Long,
        val type: ScanType = ScanType.RFID,
    )

    data class SearchScanRow(
        val code: String,
        val type: ScanType,
        val lastSeenAt: Long,
    )

    companion object {
        private const val TAG = "RfidManager"
        private const val READ_BATCH_SIZE = 500
        private const val AUTO_FIRMWARE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val BATTERY_POLL_INTERVAL_MS = 45_000L
        private val BATTERY_CONNECT_RETRY_DELAYS_MS = longArrayOf(0L, 400L, 1_200L, 2_500L, 5_000L)
    }
}
