package com.example.ai_quota_monitor_android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.ai_quota_monitor_android.MainActivity
import com.example.ai_quota_monitor_android.data.server.LocalHttpServer

/**
 * Foreground service that keeps the HTTP server alive and periodically
 * triggers WebView data refresh.
 */
class MonitorForegroundService : Service() {

    private var httpServer: LocalHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 7890) ?: 7890
        val serverEnabled = intent?.getBooleanExtra("server_enabled", true) ?: true

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AI 額度監控")
            .setContentText("監控服務執行中")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (serverEnabled && httpServer == null) {
            try {
                httpServer = LocalHttpServer(port).also { it.start() }
            } catch (e: Exception) {
                // Port might be in use
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        httpServer?.stop()
        httpServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "AI 額度監控服務",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持監控服務在背景執行"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ai_quota_monitor_service"
        const val NOTIFICATION_ID = 1
    }
}
