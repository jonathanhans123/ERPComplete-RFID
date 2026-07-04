package com.erpcomplete.rfid.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.erpcomplete.rfid.MainActivity
import com.erpcomplete.rfid.R

object WarehouseNotificationHelper {

    const val CHANNEL_ID = "warehouse_tasks"
    private const val GROUP_KEY = "warehouse_tasks_group"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_tasks),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notify_channel_tasks_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun showTaskAlert(context: Context, notificationId: Int, title: String, body: String, routeExtra: String) {
        ensureChannel(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, routeExtra)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    const val EXTRA_OPEN_ROUTE = "open_route"
    const val ROUTE_RECEIVE = "receive"
    const val ROUTE_PUTAWAY = "putaway"
    const val ROUTE_PICK = "pick"

    const val ID_RECEIPTS = 1001
    const val ID_PUTAWAY = 1002
    const val ID_PICK = 1003
}
