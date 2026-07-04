package com.erpcomplete.rfid.util

import java.util.Locale

object StatusMessage {

    private val errorMarkers = listOf(
        "error",
        "failed",
        "fail",
        "invalid",
        "denied",
        "rejected",
        "refused",
        "unable",
        "gagal",
        "kesalahan",
        "tidak valid",
        "ditolak",
        "dilarang",
        "tidak dapat",
    )

    fun looksLikeError(message: String, vararg extraMarkers: String): Boolean {
        if (message.isBlank()) return false
        if (extraMarkers.any { message.contains(it, ignoreCase = true) }) return true
        val lower = message.lowercase(Locale.getDefault())
        return errorMarkers.any { lower.contains(it) }
    }

    fun isOfflineSyncMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase(Locale.getDefault())
        return lower.contains("offline") || lower.contains("disimpan offline")
    }
}
