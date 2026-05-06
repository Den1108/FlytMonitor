package com.flyt.monitor

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.*

class MainActivity : AppCompatActivity() {

    // Это объявление ДОЛЖНО быть здесь, чтобы переменная была видна во всех функциях класса
    private lateinit var statusLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация текстового поля
        statusLogs = findViewById(R.id.statusLogs)
        val refreshButton = findViewById<Button>(R.id.refreshButton)

        refreshButton.setOnClickListener {
            checkAllServers()
        }

        // Автоматическая проверка при запуске приложения
        checkAllServers()
    }

    private fun checkAllServers() {
        statusLogs.text = "🔄 Синхронизация данных...\n"
        
        CoroutineScope(Dispatchers.Main).launch {
            // 1. Сайт Flyt RP
            val siteStatus = withContext(Dispatchers.IO) { checkPing("https://flytrp.hopto.org/") }
            logStatus("Сайт Flyt RP", siteStatus)

            // 2. SAMP Сервер (Flyt RP)
            val sampStatus = withContext(Dispatchers.IO) { checkSamp("188.127.241.8", 1389) }
            logStatus("SAMP Сервер", sampStatus)

            // 3. Твой Discord Бот (Node.js)
            val dcStatus = withContext(Dispatchers.IO) { checkPing("http://217.154.161.167:12719/") }
            logStatus("Discord Бот", dcStatus)

            // 4. Minecraft (Aternos через API)
            val mcStatus = withContext(Dispatchers.IO) { checkMinecraftViaApi("Den16459-TGYN.aternos.me", 14882) }
            logStatus("Minecraft Сервер", mcStatus)
        }
    }

    private fun logStatus(name: String, isOnline: Boolean) {
        val icon = if (isOnline) "🟢" else "🔴"
        val text = if (isOnline) "ONLINE" else "OFFLINE"
        statusLogs.append("\n$icon $name: $text")
    }

    // --- Методы проверки ---

    private fun checkPing(urlStr: String): Boolean {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 4000 
            connection.readTimeout = 4000
            connection.responseCode == 200
        } catch (e: Exception) { false }
    }

    private fun checkMinecraftViaApi(host: String, port: Int): Boolean {
        return try {
            val url = URL("https://api.mcsrvstat.us/2/$host:$port")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            val response = connection.inputStream.bufferedReader().readText()
            response.contains("\"online\":true")
        } catch (e: Exception) { false }
    }

    private fun checkSamp(ip: String, port: Int): Boolean {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 3000
            val address = InetAddress.getByName(ip)
            val parts = ip.split(".")
            if (parts.size != 4) return false

            val buffer = java.nio.ByteBuffer.allocate(11)
            buffer.put("SAMP".toByteArray())
            parts.forEach { buffer.put(it.toInt().toByte()) }
            buffer.put((port and 0xFF).toByte())
            buffer.put((port shr 8 and 0xFF).toByte())
            buffer.put('i'.toByte())

            socket.send(DatagramPacket(buffer.array(), buffer.capacity(), address, port))
            val receivePacket = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(receivePacket)
            receivePacket.length > 0
        } catch (e: Exception) { false } finally { socket?.close() }
    }
}