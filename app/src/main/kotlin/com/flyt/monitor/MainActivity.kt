package com.flyt.monitor

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusSite: TextView
    private lateinit var indicatorSite: View
    
    private lateinit var statusSamp: TextView
    private lateinit var indicatorSamp: View
    
    private lateinit var statusDiscord: TextView
    private lateinit var indicatorDiscord: View

    // Добавляем второго бота
    private lateinit var statusDiscordTwo: TextView
    private lateinit var indicatorDiscordTwo: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusSite = findViewById(R.id.statusSite)
        indicatorSite = findViewById(R.id.indicatorSite)
        
        statusSamp = findViewById(R.id.statusSamp)
        indicatorSamp = findViewById(R.id.indicatorSamp)
        
        statusDiscord = findViewById(R.id.statusDiscord)
        indicatorDiscord = findViewById(R.id.indicatorDiscord)

        statusDiscordTwo = findViewById(R.id.statusDiscordTwo)
        indicatorDiscordTwo = findViewById(R.id.indicatorDiscordTwo)

        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener { checkAllServers() }

        checkAllServers()
    }

    private fun checkAllServers() {
        resetStatuses()

        CoroutineScope(Dispatchers.Main).launch {
            // Сайт проекта
            val isSiteOnline = withContext(Dispatchers.IO) { checkPing("https://flytrp.hopto.org/") }
            updateUI(statusSite, indicatorSite, isSiteOnline)

            // SAMP Сервер
            val isSampOnline = withContext(Dispatchers.IO) { checkSamp("188.127.241.8", 1389) }
            updateUI(statusSamp, indicatorSamp, isSampOnline)

            // Discord Бот 1 (Main)
            val isDiscordOnline = withContext(Dispatchers.IO) { 
                checkPing("http://217.154.161.167:12719/") 
            }
            updateUI(statusDiscord, indicatorDiscord, isDiscordOnline)

            // Discord Бот 2 (Если адрес другой — замени тут)
            val isDiscordTwoOnline = withContext(Dispatchers.IO) {
                checkPing("http://217.154.161.167:12720/") 
            }
            updateUI(statusDiscordTwo, indicatorDiscordTwo, isDiscordTwoOnline)
        }
    }

    private fun updateUI(statusText: TextView, indicator: View, isOnline: Boolean) {
        if (isOnline) {
            statusText.text = "ONLINE"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            setIndicatorColor(indicator, "#4CAF50")
        } else {
            statusText.text = "OFFLINE"
            statusText.setTextColor(Color.parseColor("#F44336"))
            setIndicatorColor(indicator, "#F44336")
        }
    }

    private fun setIndicatorColor(view: View, colorHex: String) {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor(colorHex))
        view.background = shape
    }

    private fun resetStatuses() {
        val grey = "#9E9E9E"
        val loading = "..."
        
        val pairs = listOf(
            statusSite to indicatorSite,
            statusSamp to indicatorSamp,
            statusDiscord to indicatorDiscord,
            statusDiscordTwo to indicatorDiscordTwo
        )

        pairs.forEach { (text, indicator) ->
            text.text = loading
            text.setTextColor(Color.parseColor(grey))
            setIndicatorColor(indicator, grey)
        }
    }

    private fun checkPing(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            val responseCode = connection.responseCode
            responseCode in 200..399
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSamp(ip: String, port: Int): Boolean {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 5000
            val address = InetAddress.getByName(ip)
            val parts = ip.split(".")
            val buffer = java.nio.ByteBuffer.allocate(11)
            buffer.put("SAMP".toByteArray())
            parts.forEach { buffer.put(it.toInt().toByte()) }
            buffer.put((port and 0xFF).toByte())
            buffer.put((port shr 8 and 0xFF).toByte())
            buffer.put('i'.toByte())
            socket.send(DatagramPacket(buffer.array(), buffer.capacity(), address, port))
            val receivePacket = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(receivePacket)
            true
        } catch (e: Exception) { false } finally { socket?.close() }
    }
}