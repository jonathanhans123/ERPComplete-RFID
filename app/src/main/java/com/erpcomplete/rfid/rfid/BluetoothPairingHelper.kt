package com.erpcomplete.rfid.rfid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.erpcomplete.rfid.util.AppLog
import com.erpcomplete.rfid.util.PhoneBluetooth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object BluetoothPairingHelper {

    suspend fun pairIfNeeded(context: Context, parsed: PairingBarcodeParser.Parsed): Boolean =
        withContext(Dispatchers.IO) {
            if (!PhoneBluetooth.hasBluetoothConnectPermission(context)) {
                AppLog.w("BLUETOOTH_CONNECT permission not granted")
                return@withContext false
            }

            val adapter = PhoneBluetooth.bluetoothAdapter(context) ?: return@withContext false
            if (!adapter.isEnabled) {
                AppLog.w("Bluetooth is disabled")
                return@withContext false
            }

            findBondedDevice(adapter, parsed)?.let {
                AppLog.i("Reader already bonded: ${it.name} (${it.address})")
                return@withContext true
            }

            parsed.macAddress?.let { mac ->
                if (pairByKnownAddress(context, adapter, mac)) return@withContext true
            }

            val discovered = discoverDevice(context, adapter, parsed) ?: return@withContext false
            if (discovered.bondState == BluetoothDevice.BOND_BONDED) return@withContext true
            bondDevice(context, discovered)
        }

    @SuppressLint("MissingPermission")
    private suspend fun pairByKnownAddress(
        context: Context,
        adapter: BluetoothAdapter,
        mac: String,
    ): Boolean = runCatching {
        val device = adapter.getRemoteDevice(mac)
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                AppLog.i("Bonded via known MAC: ${device.address}")
                true
            }
            BluetoothDevice.BOND_BONDING -> {
                AppLog.i("Bond in progress for ${device.address}, waiting…")
                waitForBond(context, device) == true
            }
            else -> {
                AppLog.i("Creating bond with ${device.address}")
                bondDevice(context, device)
            }
        }
    }.onFailure { e ->
        AppLog.w("pairByKnownAddress failed for $mac: ${e.message}")
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun findBondedDevice(adapter: BluetoothAdapter, parsed: PairingBarcodeParser.Parsed): BluetoothDevice? {
        for (device in adapter.bondedDevices.orEmpty()) {
            if (matchesDevice(device, parsed)) return device
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun discoverDevice(
        context: Context,
        adapter: BluetoothAdapter,
        parsed: PairingBarcodeParser.Parsed,
    ): BluetoothDevice? = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine { cont ->
            var matched: BluetoothDevice? = null
            lateinit var receiver: BroadcastReceiver
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            } ?: return
                            if (matchesDevice(device, parsed)) {
                                matched = device
                                adapter.cancelDiscovery()
                                runCatching { context.unregisterReceiver(receiver) }
                                if (cont.isActive) cont.resume(device)
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            runCatching { context.unregisterReceiver(receiver) }
                            if (cont.isActive) {
                                cont.resume(matched)
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
                adapter.cancelDiscovery()
            }
            adapter.cancelDiscovery()
            if (!adapter.startDiscovery()) {
                runCatching { context.unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun bondDevice(context: Context, device: BluetoothDevice): Boolean =
        waitForBond(context, device) ?: false

    @SuppressLint("MissingPermission")
    private suspend fun waitForBond(context: Context, device: BluetoothDevice): Boolean? =
        withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                        val bonded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        } ?: return
                        if (bonded.address != device.address) return
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                            BluetoothDevice.BOND_BONDED -> if (cont.isActive) cont.resume(true)
                            BluetoothDevice.BOND_NONE -> if (cont.isActive) cont.resume(false)
                        }
                    }
                }
                val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                cont.invokeOnCancellation {
                    runCatching { context.unregisterReceiver(receiver) }
                }
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    runCatching { context.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(true)
                    return@suspendCancellableCoroutine
                }
                val started = runCatching {
                    device.javaClass.getMethod("createBond").invoke(device)
                    true
                }.getOrDefault(false)
                if (!started && cont.isActive) cont.resume(false)
            }
        }

    @SuppressLint("MissingPermission")
    private fun matchesDevice(device: BluetoothDevice, parsed: PairingBarcodeParser.Parsed): Boolean {
        parsed.macAddress?.let { mac ->
            if (device.address.equals(mac, ignoreCase = true)) return true
        }
        parsed.readerName?.let { name ->
            val deviceName = device.name ?: return@let
            if (deviceName.contains(name, ignoreCase = true)) return true
            if (name.contains(deviceName, ignoreCase = true)) return true
        }
        val deviceName = device.name ?: return false
        if (deviceName.startsWith("RFD", ignoreCase = true)) {
            if (parsed.readerName == null) return true
            if (deviceName.contains(parsed.readerName, ignoreCase = true)) return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    suspend fun waitForBondedRfdReader(context: Context, hint: PairingBarcodeParser.Parsed?): BluetoothDevice? =
        withContext(Dispatchers.IO) {
            val adapter = PhoneBluetooth.bluetoothAdapter(context) ?: return@withContext null
            repeat(10) {
                hint?.let { findBondedDevice(adapter, it) }?.let { return@withContext it }
                adapter.bondedDevices.orEmpty()
                    .firstOrNull { (it.name ?: "").startsWith("RFD", ignoreCase = true) }
                    ?.let { return@withContext it }
                delay(1500)
            }
            null
        }
}
