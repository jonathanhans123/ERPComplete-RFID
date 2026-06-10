package com.erpcomplete.rfid.rfid

data class FirmwareUpdateState(
    val phase: Phase = Phase.IDLE,
    val progressPercent: Int = 0,
    val message: String? = null,
    val currentVersion: String? = null,
    val availableVersion: String? = null,
    val downloadUrl: String? = null,
    val fileName: String? = null,
    val localFilePath: String? = null,
) {
    enum class Phase {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        READY_TO_INSTALL,
        INSTALLING,
        SUCCESS,
        FAILED,
    }
}
