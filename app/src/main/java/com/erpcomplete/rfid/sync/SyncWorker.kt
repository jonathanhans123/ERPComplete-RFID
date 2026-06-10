package com.erpcomplete.rfid.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.erpcomplete.rfid.ErpCompleteRfidApp

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ErpCompleteRfidApp
        return try {
            app.container.syncRepository.flush()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "erpcomplete_rfid_sync"
    }
}
