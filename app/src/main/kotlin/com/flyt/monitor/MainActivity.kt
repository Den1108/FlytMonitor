package com.flyt.monitor

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusLogs = findViewById(R.id.statusLogs)
        val refreshButton = findViewById<Button>(R.id.refreshButton)

        refreshButton.setOnClickListener {
            checkAllServers()
        }
    }

    private fun checkAllServers() {
        statusLogs.text = "Проверка...\n"
        
        CoroutineScope(Dispatchers.Main).launch {
            // 1. Сайт
            val siteStatus = withContext(Dispatchers.IO) { checkPing("https://flytrp.hopto.org/") }
            logStatus("Сайт Flyt RP", siteStatus)

            // 2. SAMP
            val sampStatus = withContext(Dispatchers.IO) { checkSamp("188.127.241.8", 1389) }
            logStatus("SAMP Сервер", sampStatus)

            // 3. Minecraft (Через API для стабильности с Aternos)
            val mcStatus = withContext(Dispatchers.IO) { checkMinecraftViaApi("Den16459-TGYN.aternos.me", 14882) }
            logStatus("Minecraft Сервер", mcStatus)
        }
    }

    private fun logStatus(name: String, isOnline: Boolean) {
        val icon = if (isOnline) "🟢" else "🔴"
        val text = if (isOnline) "ONLINE" else "OFFLINE"
        statusLogs.append("\n$icon $name: $text")
    }

    private fun checkMinecraftViaApi(host: String, port: Int): Boolean {
        return try {
            // Используем официальное API для получения статуса
            val url = URL("https://api.mcsrvstat.us/2/$host:$port")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // Даем 5 секунд, так как Aternos может "думать"
            connection.readTimeout = 5000
            
            val response = connection.inputStream.bufferedReader().readText()
            // Если в ответе JSON поле "online" равно true
            response.contains("\"online\":true")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkPing(urlStr: String): Boolean {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.responseCode == 200
        } catch (e: Exception) { false }
    }

    // Исправленный UDP пинг для SAMP (Протокол Query)
    private fun checkSamp(ip: String, port: Int): Boolean {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 3000
            val address = InetAddress.getByName(ip)
            
            val parts = ip.split(".")
            if (parts.size != 4) return false

            // Формируем пакет запроса информации (SAMP Query)
            val buffer = java.nio.ByteBuffer.allocate(11)
            buffer.put("SAMP".toByteArray())
            buffer.put(parts[0].toInt().toByte())
            buffer.put(parts[1].toInt().toByte())
            buffer.put(parts[2].toInt().toByte())
            buffer.put(parts[3].toInt().toByte())
            buffer.put((port and 0xFF).toByte())
            buffer.put((port shr 8 and 0xFF).toByte())
            buffer.put('i'.toByte()) // 'i' - запрос информации

            val packet = DatagramPacket(buffer.array(), buffer.capacity(), address, port)
            socket.send(packet)

            val receivePacket = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(receivePacket)
            
            // Если получили ответ, сервер онлайн
            receivePacket.length > 0
        } catch (e: Exception) {
            false
        } finally {
            socket?.close()
        }
    }
}