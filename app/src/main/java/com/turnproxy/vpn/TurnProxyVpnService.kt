package com.turnproxy.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.turnproxy.MainActivity
import com.turnproxy.R
import com.turnproxy.webrtc.WebRTCManager
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class TurnProxyVpnService : VpnService() {
    companion object {
        private const val TAG = "TurnProxyVpnService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "TurnProxyVPN"
        
        private const val MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_MASK = "0.0.0.0"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webrtcManager: WebRTCManager? = null
    private var dataChannelReady = false

    private var token: String? = null
    private var serverUrl: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service started")

        when (intent?.action) {
            ACTION_CONNECT -> {
                token = intent.getStringExtra(EXTRA_TOKEN)
                serverUrl = intent.getStringExtra(EXTRA_SERVER_URL)
                startVPN()
            }
            ACTION_DISCONNECT -> {
                stopVPN()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVPN() {
        if (isRunning) return

        createNotificationChannel()
        val notification = createNotification("Подключение...")
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                setupVpnInterface()
                isRunning = true
                updateNotification("Подключено", true)

                // Здесь будет подключение к WebRTC
                // Для демонстрации просто запускаем цикл чтения
                runVPNCycle()
            } catch (e: Exception) {
                Log.e(TAG, "VPN error: ${e.message}", e)
                stopVPN()
            }
        }
    }

    private fun setupVpnInterface() {
        val builder = Builder()
            .setSession("TurnProxy VPN")
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)
            .setMtu(MTU)

        vpnInterface = builder.establish()
        Log.d(TAG, "VPN interface established")
    }

    private suspend fun runVPNCycle() {
        val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(MTU)

        while (isRunning) {
            try {
                val bytesRead = vpnInput.read(buffer)
                if (bytesRead > 0) {
                    // Пакет от приложения
                    val packet = buffer.copyOf(bytesRead)
                    
                    // Отправляем через WebRTC DataChannel
                    if (dataChannelReady) {
                        webrtcManager?.sendData(packet)
                    }

                    // Для демонстрации - просто пишем обратно (эхо)
                    // В реальном приложении получаем ответ от WebRTC
                    vpnOutput.write(packet, 0, bytesRead)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "VPN cycle error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    private fun stopVPN() {
        Log.d(TAG, "Stopping VPN")
        isRunning = false
        
        vpnInterface?.close()
        vpnInterface = null

        webrtcManager?.close()
        webrtcManager = null

        serviceScope.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TurnProxy VPN",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "VPN уведомления"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String, isConnected: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TurnProxyVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TurnProxy VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отключить",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(text: String, isConnected: Boolean) {
        val notification = createNotification(text, isConnected)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CONNECT = "com.turnproxy.CONNECT"
        const val ACTION_DISCONNECT = "com.turnproxy.DISCONNECT"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_SERVER_URL = "server_url"
    }
}
