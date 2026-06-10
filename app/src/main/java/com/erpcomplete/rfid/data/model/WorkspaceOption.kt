package com.erpcomplete.rfid.data.model

data class WorkspaceOption(
    val warehouseId: Long,
    val warehouseName: String,
    val warehouseCode: String?,
    val businessUnitId: Long,
    val businessUnitName: String,
    val teamId: Long?,
    val teamName: String?,
)
