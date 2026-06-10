package com.erpcomplete.rfid.rfid

data class ReaderDiagnostics(
    val connected: Boolean = false,
    val readerName: String? = null,
    val readerAddress: String? = null,
    val modelName: String? = null,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val antennaCount: Int? = null,
    val batteryPercent: Int? = null,
    val batteryHealth: Int? = null,
    val batteryCharging: Boolean? = null,
    /** True while connected but the first battery poll has not returned yet. */
    val batteryLoading: Boolean = false,
    val scanProfile: String? = null,
    val epcPrefilterActive: Boolean = false,
    val epcPrefilterMask: String? = null,
    val lastError: String? = null,
)

sealed class TagWriteResult {
    data class Success(val writtenEpc: String) : TagWriteResult()
    data class Failure(val message: String) : TagWriteResult()
}
