package com.erpcomplete.rfid.util

/**
 * Zebra extended pairing barcode payload for "scan host barcode with the sled".
 *
 * Legacy: P + 12 hex MAC (no colons). See Zebra "Extended Pairing Barcode Format".
 * RFD90 Scan-to-Connect expects this, not a raw MAC-only Code128.
 */
object PhonePairingBarcode {

    fun payloadForPhoneMac(mac12: String): String {
        val normalized = PhoneBluetooth.normalizeMac12(mac12) ?: mac12.filter { it.isLetterOrDigit() }.uppercase()
        return "P$normalized"
    }
}
