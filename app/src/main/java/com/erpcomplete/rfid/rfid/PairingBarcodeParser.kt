package com.erpcomplete.rfid.rfid

object PairingBarcodeParser {

    private const val MAC_HEX_LENGTH = 12
    private val EXTENDED_MAC = Regex("A([0-9A-F]{12})")
    private val EMBEDDED_MAC = Regex("(?:^|[^0-9A-F])([0-9A-F]{12})(?:[^0-9A-F]|$)")

    data class Parsed(
        val macAddress: String?,
        val readerName: String?,
    )

    fun parse(raw: String): Parsed? {
        val data = raw.trim().uppercase()
        if (data.isBlank()) return null

        val alphanumeric = data.filter { it.isLetterOrDigit() }

        if (alphanumeric.startsWith("LNKB")) {
            normalizeMac12(alphanumeric.substring(4))?.let { mac ->
                return Parsed(macAddress = formatMac(mac), readerName = null)
            }
        }

        if (alphanumeric.startsWith("P")) {
            normalizeMac12(alphanumeric.substring(1))?.let { mac ->
                return Parsed(macAddress = formatMac(mac), readerName = null)
            }
        }

        for (prefix in listOf("BT", "B", "S")) {
            if (!alphanumeric.startsWith(prefix)) continue
            normalizeMac12(alphanumeric.substring(prefix.length))?.let { mac ->
                return Parsed(macAddress = formatMac(mac), readerName = null)
            }
        }

        EXTENDED_MAC.find(alphanumeric)?.groupValues?.get(1)?.let { mac ->
            return Parsed(macAddress = formatMac(mac), readerName = extractReaderName(alphanumeric))
        }

        normalizeMac12(alphanumeric)?.let { mac ->
            return Parsed(macAddress = formatMac(mac), readerName = null)
        }

        EMBEDDED_MAC.find(alphanumeric)?.groupValues?.get(1)?.let { mac ->
            return Parsed(macAddress = formatMac(mac), readerName = extractReaderName(alphanumeric))
        }

        if (alphanumeric.startsWith("RFD") && alphanumeric.length > MAC_HEX_LENGTH) {
            return Parsed(macAddress = null, readerName = alphanumeric)
        }

        return null
    }

    private fun extractReaderName(payload: String): String? {
        val idx = payload.indexOf("RFD")
        if (idx < 0) return null
        return payload.substring(idx).take(32).takeIf { it.length > 6 }
    }

    private fun normalizeMac12(raw: String): String? {
        val hex = raw.replace(":", "").replace("-", "").takeIf { it.isNotBlank() } ?: return null
        if (hex.length != MAC_HEX_LENGTH) return null
        if (!hex.all { it.isDigit() || it in 'A'..'F' }) return null
        if (hex == "020000000000") return null
        return hex
    }

    private fun formatMac(hex12: String): String =
        hex12.chunked(2).joinToString(":")
}
