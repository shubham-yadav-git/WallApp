package com.sky.wallapp.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sky.wallapp.FcmWallpaperTarget
import com.sky.wallapp.MainActivity
import com.sky.wallapp.R

class WallAppMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Firebase manages this token. Never log or expose it from the client.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val validWallpaperKey = FcmWallpaperTarget.key(
            data[EXTRA_WALLPAPER_KEY],
            data[EXTRA_CATEGORY],
            data[EXTRA_WALLPAPER_ID]
        )
        val notification = remoteMessage.notification
        val title = notification?.title?.takeIf { it.isNotBlank() }
            ?: data[KEY_TITLE]?.takeIf { it.isNotBlank() }
            ?: if (validWallpaperKey != null) getString(R.string.new_wallpaper_notification) else null
        val body = notification?.body?.takeIf { it.isNotBlank() }
            ?: data[KEY_BODY]?.takeIf { it.isNotBlank() }
            ?: if (validWallpaperKey != null) getString(R.string.new_wallpaper_notification_body) else null

        if (title == null && body == null) return
        showNotification(
            title = title ?: getString(R.string.app_name),
            body = body.orEmpty(),
            wallpaperKey = validWallpaperKey,
            notificationId = remoteMessage.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
        )
    }

    private fun showNotification(title: String, body: String, wallpaperKey: String?, notificationId: Int) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            wallpaperKey?.let { putExtra(EXTRA_WALLPAPER_KEY, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission is unavailable", e)
        }
    }

    companion object {
        private const val TAG = "WallAppMessaging"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"

        const val CHANNEL_ID = "wallapp_general_channel"
        const val EXTRA_WALLPAPER_KEY = "wallpaper_key"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_WALLPAPER_ID = "wallpaper_id"
    }
}
