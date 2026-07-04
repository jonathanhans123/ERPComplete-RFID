package com.erpcomplete.rfid.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log

object PhoneBluetooth {

    private const val TAG = "PhoneBluetooth"

    enum class MacSource {
        ADAPTER,
        SETTINGS_SECURE,
        SETTINGS_GLOBAL,
        SYSTEM_PROPERTY,
        UNAVAILABLE,
    }

    data class ResolvedMac(
        val mac12: String,
        val formatted: String,
        val source: MacSource,
    )

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("HardwareIds")
    fun resolveLocalMac(context: Context): ResolvedMac? {
        if (!hasBluetoothConnectPermission(context)) return null
        return runCatching {
            val adapter = bluetoothAdapter(context) ?: return@runCatching null
            if (!adapter.isEnabled) return@runCatching null

            @Suppress("DEPRECATION")
            fromAdapter(readAdapterAddress(adapter))?.let { return@runCatching it }

            readSettingsMac(context, secure = true)?.let { return@runCatching it }
            readSettingsMac(context, secure = false)?.let { return@runCatching it }
            trySystemProperty(
                "persist.service.bdroid.address",
                "ro.boot.btmacaddr",
                "persist.odm.bt.address",
                "persist.vendor.bluetooth.bdaddr",
            )?.let { return@runCatching it }

            null
        }.onFailure { e ->
            Log.w(TAG, "resolveLocalMac failed: ${e.message}")
        }.getOrNull()
    }

    /** @deprecated Use [resolveLocalMac] */
    @SuppressLint("HardwareIds")
    fun localMacAddress(context: Context): String? =
        resolveLocalMac(context)?.formatted

    fun macForPairingBarcode(context: Context): String? =
        resolveLocalMac(context)?.mac12

    fun normalizeMac12(raw: String?): String? {
        val hex = raw?.trim()?.uppercase()?.replace(":", "")?.replace("-", "")?.takeIf { it.isNotBlank() }
            ?: return null
        if (hex.length != 12 || !hex.all { it.isDigit() || it in 'A'..'F' }) return null
        if (hex == "020000000000") return null
        return hex
    }

    /**
     * Parse phone / reader barcodes, QR JSON, wedge scanner input, and labeled MAC text.
     */
    fun parseMacInput(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().trimEnd('\r', '\n', '\t', ' ')
        if (trimmed.isBlank()) return null

        Regex(""""bluetooth(?:_address)?"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.get(1)?.let { normalizeMac12(it)?.let { mac -> return mac } }

        Regex("""bluetooth(?:\s*address)?\s*[:=]\s*([0-9A-Fa-f:.\-\s]+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.get(1)?.let { normalizeMac12(it)?.let { mac -> return mac } }

        Regex("""\b([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\b""")
            .find(trimmed)?.groupValues?.get(1)?.let { normalizeMac12(it)?.let { mac -> return mac } }

        Regex("""\b([0-9A-Fa-f]{2}(?:-[0-9A-Fa-f]{2}){5})\b""")
            .find(trimmed)?.groupValues?.get(1)?.let { normalizeMac12(it)?.let { mac -> return mac } }

        com.erpcomplete.rfid.rfid.PairingBarcodeParser.parse(trimmed)?.macAddress
            ?.let { normalizeMac12(it)?.let { mac -> return mac } }

        val hexOnly = trimmed.uppercase().filter { it.isDigit() || it in 'A'..'F' }
        if (hexOnly.length == 12) {
            normalizeMac12(hexOnly)?.let { mac -> return mac }
        }
        Regex("""(?:^|[^0-9A-F])([0-9A-F]{12})(?:[^0-9A-F]|$)""", RegexOption.IGNORE_CASE)
            .find(hexOnly)?.groupValues?.get(1)?.let { normalizeMac12(it)?.let { mac -> return mac } }

        return normalizeMac12(trimmed)
    }

    fun formatMac(hex12: String): String =
        hex12.chunked(2).joinToString(":")

    fun bluetoothAdapter(context: Context): BluetoothAdapter? = runCatching {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
    }.getOrNull()

    fun isEnabled(context: Context): Boolean = bluetoothAdapter(context)?.isEnabled == true

    @SuppressLint("HardwareIds")
    private fun readAdapterAddress(adapter: BluetoothAdapter): String? = runCatching {
        @Suppress("DEPRECATION")
        adapter.address
    }.getOrNull()

    private fun readSettingsMac(context: Context, secure: Boolean): ResolvedMac? = runCatching {
        val raw = if (secure) {
            Settings.Secure.getString(context.contentResolver, "bluetooth_address")
        } else {
            Settings.Global.getString(context.contentResolver, "bluetooth_address")
        }
        fromRaw(raw)?.copy(
            source = if (secure) MacSource.SETTINGS_SECURE else MacSource.SETTINGS_GLOBAL,
        )
    }.getOrNull()

    private fun fromAdapter(raw: String?): ResolvedMac? =
        fromRaw(raw)?.copy(source = MacSource.ADAPTER)

    private fun fromRaw(raw: String?): ResolvedMac? {
        val mac12 = normalizeMac12(raw) ?: return null
        return ResolvedMac(
            mac12 = mac12,
            formatted = formatMac(mac12),
            source = MacSource.ADAPTER,
        )
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun trySystemProperty(vararg keys: String): ResolvedMac? {
        for (key in keys) {
            val resolved = runCatching {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getMethod("get", String::class.java, String::class.java)
                val raw = get.invoke(null, key, "") as String
                fromRaw(raw)?.copy(source = MacSource.SYSTEM_PROPERTY)
            }.getOrNull()
            if (resolved != null) return resolved
        }
        return null
    }
}
