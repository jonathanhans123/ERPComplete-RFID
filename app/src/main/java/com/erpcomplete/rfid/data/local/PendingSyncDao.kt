package com.erpcomplete.rfid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {
    @Insert
    suspend fun insert(item: PendingSyncEntity): Long

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC LIMIT :limit")
    suspend fun oldest(limit: Int = 50): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_sync SET attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_sync WHERE attemptCount >= :minAttempts ORDER BY createdAt ASC LIMIT :limit")
    suspend fun failed(minAttempts: Int = 5, limit: Int = 50): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync WHERE attemptCount >= :minAttempts ORDER BY createdAt ASC LIMIT :limit")
    fun observeFailed(minAttempts: Int = 5, limit: Int = 50): Flow<List<PendingSyncEntity>>

    @Query("DELETE FROM pending_sync")
    suspend fun clearAll()
}
