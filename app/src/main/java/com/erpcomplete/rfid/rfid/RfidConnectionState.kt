package com.erpcomplete.rfid.rfid

sealed interface RfidConnectionState {
    data object Idle : RfidConnectionState

    data object ScanningReaders : RfidConnectionState

    data object Pairing : RfidConnectionState

    data class ReadersFound(val count: Int) : RfidConnectionState

    data class Connected(val readerName: String) : RfidConnectionState

    data class Error(val message: String) : RfidConnectionState
}
