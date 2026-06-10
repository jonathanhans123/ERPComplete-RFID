package com.erpcomplete.rfid.util

import com.erpcomplete.rfid.data.AppContainer

/** Resolved workspace IDs from AuthStore (sent as API headers + optional query params). */
data class WorkspaceContext(
    val businessUnitId: Long?,
    val teamId: Long?,
    val warehouseId: Long?,
)

fun AppContainer.workspaceContext(): WorkspaceContext = WorkspaceContext(
    businessUnitId = authStore.businessUnitIdBlocking()?.toLongOrNull(),
    teamId = authStore.teamIdBlocking()?.toLongOrNull(),
    warehouseId = authStore.warehouseIdBlocking()?.toLongOrNull(),
)
