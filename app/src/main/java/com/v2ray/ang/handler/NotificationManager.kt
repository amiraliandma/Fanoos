package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.main.MainActivity

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1

    private var mBuilder: NotificationCompat.Builder? = null
    private var mNotificationManager: NotificationManager? = null

    fun setStatusLine(text: String?) {
        val builder = mBuilder ?: return
        builder.setContentText(text)
        getNotificationManager()?.notify(NOTIFICATION_ID, builder.build())
    }

    fun startSpeedNotification() {
        // Minimal mode: no periodic traffic polling needed for battery & lightness.
    }

    fun stopSpeedNotification() {
        // No-op in minimal mode.
    }

    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags
        )

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(
            service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags
        )

        val channelId = createNotificationChannel()
        val serverName = currentConfig?.remarks?.takeIf { it.isNotBlank() }
        val title = service.getString(R.string.app_name)
        val content = if (serverName != null) {
            service.getString(R.string.notification_connected, serverName)
        } else {
            service.getString(R.string.toast_services_start)
        }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_fanoos)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                service.getString(R.string.notification_action_stop),
                stopV2RayPendingIntent
            )

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null) else service.startForeground(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        getNotificationManager()?.cancel(NOTIFICATION_ID)
        mBuilder = null
        mNotificationManager = null
    }

    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val service = getService() ?: return channelId
        NotificationHelper.ensureNotificationChannel(
            context = service,
            channelId = channelId,
            channelNameRes = R.string.notification_channel_vpn,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        return channelId
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
