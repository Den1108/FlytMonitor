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
            // Проверка сайта Flyt RP
            val siteStatus = withContext(Dispatchers.IO) { checkPing("https://flytrp.hopto.org/") } // Замени на свой домен
            logStatus("Сайт Flyt RP", siteStatus)

            // Проверка SAMP сервера
            val sampStatus = withContext(Dispatchers.IO) { checkSamp("188.127.241.8", 1389) }
            logStatus("SAMP Сервер", sampStatus)

            // Проверка Minecraft
            val mcStatus = withContext(Dispatchers.IO) { checkTcp( "Den16459-TGYN.aternos.me", 14882) }
            logStatus("Minecraft Сервер", mcStatus)
        }
    }

    private fun logStatus(name: String, isOnline: Boolean) {
        val icon = if (isOnline) "🟢" else "🔴"
        val text = if (isOnline) "ONLINE" else "OFFLINE"
        statusLogs.append("\n$icon $name: $text")
    }

    // Универсальный TCP пинг (для Minecraft/Сайтов)
    private fun checkTcp(host: String, port: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), 2000) }
            true
        } catch (e: Exception) { false }
    }

    private fun checkPing(urlStr: String): Boolean {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.responseCode == 200
        } catch (e: Exception) { false }
    }

    // Специальный UDP пинг для SAMP
    private fun checkSamp(ip: String, port: Int): Boolean {
        return try {
            val socket = DatagramSocket()
            socket.soTimeout = 2000
            val address = InetAddress.getByName(ip)
            val packetData = "SAMP".toByteArray() + byteArrayOf((port and 0xFF).toByte(), (port shr 8 and 0xFF).toByte(), 'i'.toByte())
            socket.send(DatagramPacket(packetData, packetData.size, address, port))
            socket.receive(DatagramPacket(ByteArray(1024), 1024))
            true
        } catch (e: Exception) { false }
    }
}