package com.reelhouse.app.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.reelhouse.app.R
import com.reelhouse.app.ReelhouseApp

/**
 * Foreground service that keeps the app (and its local streaming server) alive while casting,
 * so the Chromecast keeps receiving the video even if the phone screen turns off.
 */
class CastServerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Casting", NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cast)
            .setContentTitle("Reelhouse")
            .setContentText("Casting to your TV")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        StreamServer.start(application as ReelhouseApp)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        StreamServer.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "cast"
        private const val NOTIF_ID = 42

        fun start(ctx: Context) {
            // Start the HTTP server immediately so the TV can connect right away.
            StreamServer.start(ctx.applicationContext as ReelhouseApp)
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, CastServerService::class.java))
            } catch (e: Exception) {
                // Server still runs while the app is in the foreground.
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CastServerService::class.java))
            StreamServer.stop()
        }
    }
}
