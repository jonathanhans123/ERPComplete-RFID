package com.erpcomplete.rfid.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.erpcomplete.rfid.ErpCompleteRfidApp
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.NotificationSettingsStore
import com.erpcomplete.rfid.util.WorkflowJson
import com.erpcomplete.rfid.util.workspaceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as ErpCompleteRfidApp).container
        val session = container.authStore.readSessionSnapshot()
        if (!session.loggedIn || !session.hasWorkspace) {
            return@withContext Result.success()
        }
        if (!container.notificationSettingsStore.taskAlertsEnabledBlocking()) {
            return@withContext Result.success()
        }

        val whId = try {
            container.workspaceContext().warehouseId
        } catch (_: Exception) {
            return@withContext Result.success()
        }

        val counts = try {
            fetchCounts(container, whId)
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        val store = container.notificationSettingsStore
        val previous = store.readLastCounts()
        val ctx = applicationContext

        if (counts.receipts > previous.receipts) {
            WarehouseNotificationHelper.showTaskAlert(
                ctx,
                WarehouseNotificationHelper.ID_RECEIPTS,
                ctx.getString(R.string.notify_new_receipts_title),
                ctx.getString(R.string.notify_new_receipts_body, counts.receipts),
                WarehouseNotificationHelper.ROUTE_RECEIVE,
            )
        }
        if (counts.putaway > previous.putaway) {
            WarehouseNotificationHelper.showTaskAlert(
                ctx,
                WarehouseNotificationHelper.ID_PUTAWAY,
                ctx.getString(R.string.notify_new_putaway_title),
                ctx.getString(R.string.notify_new_putaway_body, counts.putaway),
                WarehouseNotificationHelper.ROUTE_PUTAWAY,
            )
        }
        if (counts.pick > previous.pick) {
            WarehouseNotificationHelper.showTaskAlert(
                ctx,
                WarehouseNotificationHelper.ID_PICK,
                ctx.getString(R.string.notify_new_pick_title),
                ctx.getString(R.string.notify_new_pick_body, counts.pick),
                WarehouseNotificationHelper.ROUTE_PICK,
            )
        }

        store.writeLastCounts(
            NotificationSettingsStore.TaskCounts(
                receipts = counts.receipts,
                putaway = counts.putaway,
                pick = counts.pick,
            ),
        )
        Result.success()
    }

    private suspend fun fetchCounts(container: com.erpcomplete.rfid.data.AppContainer, warehouseId: Long?): NotificationSettingsStore.TaskCounts {
        val wh = warehouseId
        val pending = container.api.listGoodsReceipts(status = "pending", warehouseId = wh, perPage = 1)
        val partial = container.api.listGoodsReceipts(status = "partial", warehouseId = wh, perPage = 1)
        val putaway = container.api.listPutawayTasks(status = "pending,in_progress", warehouseId = wh, perPage = 1)
        val pick = container.api.listPickLists(pickStatus = "pending,in_progress", warehouseId = wh, perPage = 1)

        val receipts = (if (pending.isSuccessful) WorkflowJson.envelopeCount(pending) else 0) +
            (if (partial.isSuccessful) WorkflowJson.envelopeCount(partial) else 0)
        val putawayCount = if (putaway.isSuccessful) WorkflowJson.envelopeCount(putaway) else 0
        val pickCount = if (pick.isSuccessful) WorkflowJson.envelopeCount(pick) else 0

        return NotificationSettingsStore.TaskCounts(
            receipts = receipts,
            putaway = putawayCount,
            pick = pickCount,
        )
    }

    companion object {
        const val UNIQUE_NAME = "warehouse_task_notifications"
    }
}
