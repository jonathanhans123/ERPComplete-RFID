package com.erpcomplete.rfid.rfid

import android.content.Context
import com.erpcomplete.rfid.util.AppLog
import com.zebra.barcode.sdk.BarcodeDataEventArgs
import com.zebra.barcode.sdk.BarcodeDataListener
import com.zebra.barcode.sdk.BarcodeScanner
import com.zebra.barcode.sdk.BarcodeScannerInfo
import com.zebra.barcode.sdk.BarcodeScannerSdk
import com.zebra.barcode.sdk.BarcodeScannerType

/**
 * Connects the integrated barcode imager on RFD40/RFD90 via Zebra's BarcodeScanner SDK
 * (bundled inside rfidapi3lib). Bottom trigger scans arrive through [BarcodeDataListener].
 */
class HandheldBarcodeBridge(private val appContext: Context) {

    private var scanner: BarcodeScanner? = null

    fun initSdk() {
        runCatching { BarcodeScannerSdk.setContext(appContext.applicationContext) }
            .onFailure { AppLog.w("Barcode SDK init failed: ${it.message}") }
    }

    fun attach(readerName: String?, readerAddress: String?, onBarcode: (String) -> Unit) {
        detach()
        runCatching {
            val manager = BarcodeScannerSdk.getBarcodeScannerManagementServicesFactory()
                .createBarcodeScannerManager(BarcodeScannerType.BLUETOOTH)
            val scanners = manager.scanners ?: emptyList()
            val info = pickScanner(scanners, readerName, readerAddress) ?: run {
                AppLog.w("No Bluetooth barcode scanner found (reader=$readerName)")
                return
            }
            val scan = BarcodeScannerSdk.getBarcodeScannerFactory().create(info)
            val listener = BarcodeDataListener { args ->
                decodeBarcode(args)?.let(onBarcode)
            }
            scan.addBarcodeDataListener(listener)
            scan.connect()
            scanner = scan
            AppLog.i("Barcode scanner connected: ${scannerName(info)}")
        }.onFailure { AppLog.w("Barcode scanner attach failed: ${it.message}") }
    }

    fun detach() {
        runCatching {
            scanner?.takeIf { it.isConnected }?.disconnect()
        }
        scanner = null
    }

    private fun pickScanner(
        scanners: List<BarcodeScannerInfo>,
        readerName: String?,
        readerAddress: String?,
    ): BarcodeScannerInfo? {
        readerAddress?.takeIf { it.isNotBlank() }?.let { addr ->
            scanners.firstOrNull { scanner ->
                scannerName(scanner).contains(addr, ignoreCase = true)
            }?.let { return it }
        }
        readerName?.takeIf { it.isNotBlank() }?.let { name ->
            scanners.firstOrNull { scanner -> scannerName(scanner).equals(name, ignoreCase = true) }?.let { return it }
            scanners.firstOrNull { scanner -> scannerName(scanner).contains(name, ignoreCase = true) }?.let { return it }
            scanners.firstOrNull { scanner -> name.contains(scannerName(scanner), ignoreCase = true) }?.let { return it }
        }
        return scanners.firstOrNull { scanner -> scannerName(scanner).startsWith("RFD", ignoreCase = true) }
            ?: scanners.firstOrNull()
    }

    private fun scannerName(scanner: BarcodeScannerInfo): String = scanner.name

    private fun decodeBarcode(args: BarcodeDataEventArgs): String? {
        val raw = args.barcodeData ?: return null
        val text = String(raw, Charsets.UTF_8).trim()
        return text.takeIf { it.isNotBlank() }
    }
}
