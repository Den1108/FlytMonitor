package com.flyt.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class MonitorService : Service() {

    private val CHANNEL_ID = "flyt_monitor_channel"
    private val CHANNEL_FOREGROUND_ID = "flyt_monitor_foreground"
    private val CHECK_INTERVAL = 60_000L // 60 секунд
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prevStatus = mutableMapOf<String, Boolean?>()

    data class Server(val key: String, val url: String, val name: String)
    private val servers = listOf(
        Server("site",       "https://flytrp.hopto.org/",          "Веб-сайт"),
        Server("discord",    "http://217.154.161.167:12719/",       "Main Bot"),
        Server("discordTwo", "http://212.132.120.102:10237/",       "Moderation Bot")
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        startForeground(1, buildForegroundNotification())
        startMonitoring()
        return START_STICKY // Перезапускается автоматически если система убила сервис
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                checkAllServers()
                delay(CHECK_INTERVAL)
            }
        }
    }

    private suspend fun checkAllServers() {
        for (server in servers) {
            val (isOnline, _) = checkServer(server.url)
            val wasOnline = prevStatus[server.key]
            if (wasOnline != null && wasOnline != isOnline) {
                if (!isOnline) {
                    sendAlert("⚠️ ${server.name} недоступен", "${server.name} перешёл в состояние OFFLINE")
                } else {
                    sendAlert("✅ ${server.name} восстановлен", "${server.name} снова ONLINE")
                }
            }
            prevStatus[server.key] = isOnline
        }
    }

    private fun checkServer(urlStr: String): Pair<Boolean, Long> {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            val start = System.currentTimeMillis()
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            conn.disconnect()
            Pair(code in 200..399, elapsed)
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    private fun sendAlert(title: String, message: String) {
        val notifId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
        } catch (_: SecurityException) {}
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Flyt Monitor работает")
            .setContentText("Мониторинг серверов активен")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Канал для алертов
            mgr.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Flyt Monitor Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Изменения статуса серверов"
                enableLights(true)
                lightColor = Color.parseColor("#00E5A0")
            })
            // Канал для foreground уведомления (тихий)
            mgr.createNotificationChannel(NotificationChannel(
                CHANNEL_FOREGROUND_ID, "Мониторинг (фон)", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное уведомление о работе мониторинга"
            })
        }
    }
}