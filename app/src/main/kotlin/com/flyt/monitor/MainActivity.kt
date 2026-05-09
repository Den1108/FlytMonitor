package com.flyt.monitor

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // ── UI refs ──────────────────────────────────────────────────────────────
    private lateinit var statusSite: TextView
    private lateinit var indicatorSite: View
    private lateinit var pingSite: TextView

    private lateinit var statusDiscord: TextView
    private lateinit var indicatorDiscord: View
    private lateinit var pingDiscord: TextView

    private lateinit var statusDiscordTwo: TextView
    private lateinit var indicatorDiscordTwo: View
    private lateinit var pingDiscordTwo: TextView

    private lateinit var lastUpdateText: TextView
    private lateinit var nextRefreshText: TextView
    private lateinit var refreshButton: Button
    private lateinit var notifButton: Button

    // ── State ─────────────────────────────────────────────────────────────────
    private val prevStatus = mutableMapOf<String, Boolean?>()   // null = unknown
    private var notificationsEnabled = false
    private var autoRefreshJob: Job? = null
    private var countdownJob: Job? = null
    private val AUTO_REFRESH_INTERVAL = 60L   // секунд
    private val CHANNEL_ID = "flyt_monitor_channel"
    private val NOTIF_PERMISSION_REQUEST = 101

    // ── Server configs ────────────────────────────────────────────────────────
    data class Server(val key: String, val url: String)

    private val servers = listOf(
        Server("site",       "https://flytrp.hopto.org/"),
        Server("discord",    "http://217.154.161.167:12719/"),
        Server("discordTwo", "http://212.132.120.102:10237/")
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        createNotificationChannel()

        refreshButton.setOnClickListener {
            animateButton(refreshButton)
            restartAutoRefresh()
        }

        notifButton.setOnClickListener {
            toggleNotifications()
        }

        startAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRefreshJob?.cancel()
        countdownJob?.cancel()
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        statusSite      = findViewById(R.id.statusSite)
        indicatorSite   = findViewById(R.id.indicatorSite)
        pingSite        = findViewById(R.id.pingSite)

        statusDiscord   = findViewById(R.id.statusDiscord)
        indicatorDiscord = findViewById(R.id.indicatorDiscord)
        pingDiscord     = findViewById(R.id.pingDiscord)

        statusDiscordTwo   = findViewById(R.id.statusDiscordTwo)
        indicatorDiscordTwo = findViewById(R.id.indicatorDiscordTwo)
        pingDiscordTwo     = findViewById(R.id.pingDiscordTwo)

        lastUpdateText  = findViewById(R.id.lastUpdateText)
        nextRefreshText = findViewById(R.id.nextRefreshText)
        refreshButton   = findViewById(R.id.refreshButton)
        notifButton     = findViewById(R.id.notifButton)
    }

    // ── Auto-refresh logic ────────────────────────────────────────────────────
    private fun startAutoRefresh() {
        autoRefreshJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                checkAllServers()
                startCountdown()
                delay(AUTO_REFRESH_INTERVAL * 1000)
            }
        }
    }

    private fun restartAutoRefresh() {
        autoRefreshJob?.cancel()
        countdownJob?.cancel()
        startAutoRefresh()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = CoroutineScope(Dispatchers.Main).launch {
            var remaining = AUTO_REFRESH_INTERVAL
            while (remaining > 0 && isActive) {
                nextRefreshText.text = "Следующее обновление через ${remaining}с"
                delay(1000)
                remaining--
            }
        }
    }

    // ── Check all servers ─────────────────────────────────────────────────────
    private fun checkAllServers() {
        resetStatuses()
        if (!hasInternetConnection()) {
            statusSite.text = "NO INTERNET"
            statusDiscord.text = "NO INTERNET"
            statusDiscordTwo.text = "NO INTERNET"

            return
        }
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lastUpdateText.text = "Обновление: ${sdf.format(Date())}"

        CoroutineScope(Dispatchers.Main).launch {
            // Site
            val (siteOnline, sitePing) = withContext(Dispatchers.IO) { checkServer("https://flytrp.hopto.org/") }
            updateUI("site", statusSite, indicatorSite, pingSite, siteOnline, sitePing)

            // Bot 1
            val (d1Online, d1Ping) = withContext(Dispatchers.IO) { checkServer("http://217.154.161.167:12719/") }
            updateUI("discord", statusDiscord, indicatorDiscord, pingDiscord, d1Online, d1Ping)

            // Bot 2
            val (d2Online, d2Ping) = withContext(Dispatchers.IO) { checkServer("http://212.132.120.102:10237/") }
            updateUI("discordTwo", statusDiscordTwo, indicatorDiscordTwo, pingDiscordTwo, d2Online, d2Ping)
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── Server check with latency ─────────────────────────────────────────────
    private fun checkServer(urlStr: String): Pair<Boolean, Long> {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            val start = System.currentTimeMillis()
            val code = connection.responseCode
            val elapsed = System.currentTimeMillis() - start
            connection.disconnect()
            Pair(code in 200..399, elapsed)
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    // ── UI update ─────────────────────────────────────────────────────────────
    private fun updateUI(
        key: String,
        statusText: TextView,
        indicator: View,
        pingText: TextView,
        isOnline: Boolean,
        latency: Long
    ) {
        val wasOnline = prevStatus[key]

        if (isOnline) {
            statusText.text = "ONLINE"
            statusText.setTextColor(Color.parseColor("#00E5A0"))
            setIndicatorColor(indicator, "#00E5A0")
            pingText.text = if (latency >= 0) "${latency} ms" else "— ms"
            pingText.setTextColor(getPingColor(latency))
        } else {
            statusText.text = "OFFLINE"
            statusText.setTextColor(Color.parseColor("#FF4757"))
            setIndicatorColor(indicator, "#FF4757")
            pingText.text = "timeout"
            pingText.setTextColor(Color.parseColor("#FF4757"))
        }

        // Notify on status change
        if (wasOnline != null && wasOnline != isOnline && notificationsEnabled) {
            val serverName = getServerName(key)
            if (!isOnline) {
                sendNotification("⚠️ $serverName недоступен", "$serverName перешёл в состояние OFFLINE")
            } else {
                sendNotification("✅ $serverName восстановлен", "$serverName снова ONLINE")
            }
        }

        prevStatus[key] = isOnline
        pulseIndicator(indicator, isOnline)
    }

    private fun getPingColor(ms: Long): Int {
        return when {
            ms < 0    -> Color.parseColor("#4A5578")
            ms < 200  -> Color.parseColor("#00E5A0")
            ms < 600  -> Color.parseColor("#FFD166")
            else      -> Color.parseColor("#FF4757")
        }
    }

    private fun getServerName(key: String) = when (key) {
        "site"       -> "Веб-сайт"
        "discord"    -> "Main Bot"
        "discordTwo" -> "Moderation Bot"
        else         -> key
    }

    // ── Reset UI ──────────────────────────────────────────────────────────────
    private fun resetStatuses() {
        val pairs = listOf(
            Triple(statusSite, indicatorSite, pingSite),
            Triple(statusDiscord, indicatorDiscord, pingDiscord),
            Triple(statusDiscordTwo, indicatorDiscordTwo, pingDiscordTwo)
        )
        pairs.forEach { (text, indicator, ping) ->
            text.text = "..."
            text.setTextColor(Color.parseColor("#4A5578"))
            setIndicatorColor(indicator, "#4A5578")
            ping.text = "— ms"
            ping.setTextColor(Color.parseColor("#4A5578"))
        }
    }

    // ── Indicator styling ─────────────────────────────────────────────────────
    private fun setIndicatorColor(view: View, colorHex: String) {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor(colorHex))
        view.background = shape
    }

    private fun pulseIndicator(view: View, isOnline: Boolean) {
        if (!isOnline) return
        val animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.2f, 1f)
        animator.duration = 1200
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }

    // ── Button animation ──────────────────────────────────────────────────────
    private fun animateButton(btn: Button) {
        val scaleDown = ObjectAnimator.ofFloat(btn, "scaleX", 1f, 0.94f)
        scaleDown.duration = 80
        scaleDown.start()
        ObjectAnimator.ofFloat(btn, "scaleY", 1f, 0.94f).also {
            it.duration = 80; it.start()
        }
        btn.postDelayed({
            ObjectAnimator.ofFloat(btn, "scaleX", 0.94f, 1f).also { it.duration = 100; it.start() }
            ObjectAnimator.ofFloat(btn, "scaleY", 0.94f, 1f).also { it.duration = 100; it.start() }
        }, 100)
    }

    // ── Notifications ─────────────────────────────────────────────────────────
    private fun toggleNotifications() {
        if (!notificationsEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIF_PERMISSION_REQUEST
                    )
                    return
                }
            }
            enableNotifications()
        } else {
            notificationsEnabled = false
            notifButton.text = "🔔"
            notifButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#0F1628")
            )
        }
    }

    private fun enableNotifications() {
        notificationsEnabled = true
        notifButton.text = "🔕"
        notifButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor("#1A2A1A")
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIF_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableNotifications()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flyt Monitor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления об изменении статуса серверов"
                enableLights(true)
                lightColor = Color.parseColor("#00E5A0")
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        val notifId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(notifId, notification)
    }
}
