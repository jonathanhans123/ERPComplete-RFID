package com.erpcomplete.rfid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endpoint: String,
    val method: String,
    val bodyJson: String,
    val idempotencyKey: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val lastError: String? = null,
    /** Workspace the request was queued in; replayed as headers so a later workspace switch cannot redirect it. */
    val businessUnitId: String? = null,
    val teamId: String? = null,
    val warehouseId: String? = null,
)
