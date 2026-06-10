package com.erpcomplete.rfid.data.model

data class BusinessUnitSummary(
    val id: Long,
    val name: String,
    val warehouseCount: Int = 0,
)

data class WorkspacesPayload(
    val businessUnits: List<BusinessUnitSummary>,
    val warehouses: List<WorkspaceOption>,
)
