package com.turnproxy.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.turnproxy.R
import com.turnproxy.vpn.TurnProxyVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import com.turnproxy.webrtc.WebRTCManager
import com.turnproxy.signaling.SignalingManager

class MainActivity : AppCompatActivity() {
    private lateinit var tokenInput: EditText
    private lateinit var serverUrlInput: EditText
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var trafficText: TextView

    private var isVpnRunning = false
    private var signalingManager: SignalingManager? = null
    private var webrtcManager: WebRTCManager? = null

    private var bytesReceived = 0L
    private var bytesSent = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        startTrafficStats()
    }

    private fun initViews() {
        tokenInput = findViewById(R.id.tokenInput)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        trafficText = findViewById(R.id.trafficText)

        // Default server URL
        serverUrlInput.setText("ws://194.76.172.67:3001/ws")
    }

    private fun setupListeners() {
        connectButton.setOnClickListener {
            if (isVpnRunning) {
                disconnectVPN()
            } else {
                connectVPN()
            }
        }
    }

    private fun connectVPN() {
        val token = tokenInput.text.toString().trim()
        val serverUrl = serverUrlInput.text.toString().trim()

        if (token.isEmpty()) {
            showError("Введите device token")
            return
        }

        if (serverUrl.isEmpty()) {
            showError("Введите URL сервера")
            return
        }

        updateStatus("Подключение...")
        connectButton.isEnabled = false

        lifecycleScope.launch {
            try {
                // Запускаем VPN сервис
                val intent = Intent(this@MainActivity, TurnProxyVpnService::class.java).apply {
                    action = TurnProxyVpnService.ACTION_CONNECT
                    putExtra(TurnProxyVpnService.EXTRA_TOKEN, token)
                    putExtra(TurnProxyVpnService.EXTRA_SERVER_URL, serverUrl)
                }
                startForegroundService(intent)

                // Запускаем WebRTC signaling
                startWebRTC(serverUrl, token)

                isVpnRunning = true
                updateStatus("Подключено")
                updateConnectButton(true)
                vibrateSuccess()

            } catch (e: Exception) {
                showError("Ошибка подключения: ${e.message}")
                updateConnectButton(false)
            }
        }
    }

    private fun disconnectVPN() {
        val intent = Intent(this, TurnProxyVpnService::class.java).apply {
            action = TurnProxyVpnService.ACTION_DISCONNECT
        }
        startService(intent)

        signalingManager?.disconnect()
        webrtcManager?.close()

        isVpnRunning = false
        bytesReceived = 0L
        bytesSent = 0L

        updateStatus("Отключено")
        updateConnectButton(false)
    }

    private suspend fun startWebRTC(serverUrl: String, token: String) {
        // ICE серверы (TURN + STUN)
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:194.76.172.67:3478").createIceServer(),
            PeerConnection.IceServer.builder("turn:194.76.172.67:3478?transport=udp")
                .setUsername(turnUsername)
                .setPassword(turnPassword)
                .createIceServer()
        )

        webrtcManager = WebRTCManager(object : WebRTCManager.WebRTCListener {
            override fun onDataChannelReady() {
                updateStatus("DataChannel открыт")
            }

            override fun onDataReceived(data: ByteArray) {
                bytesReceived += data.size
                // Обработка данных от сервера
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                // Отправляем ICE кандидата серверу
                signalingManager?.sendIceCandidate(
                    candidate.sdp,
                    candidate.sdpMid ?: "",
                    candidate.sdpMLineIndex
                )
            }

            override fun onConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        updateStatus("WebRTC подключен")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        updateStatus("WebRTC отключен")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        updateStatus("WebRTC ошибка")
                        disconnectVPN()
                    }
                    else -> {}
                }
            }
        }, iceServers)

        webrtcManager?.createPeerConnection()
        webrtcManager?.createDataChannel()

        // Запускаем signaling
        signalingManager = SignalingManager(serverUrl, token, object : SignalingManager.SignalingListener {
            override fun onAuthenticated(sessionId: String) {
                updateStatus("Аутентифицирован: $sessionId")
                
                // Создаем offer после аутентификации
                createOffer()
            }

            override fun onOffer(sdp: String) {
                webrtcManager?.setRemoteDescription(sdp, "offer")
                val answerSdp = webrtcManager?.createAnswer()
                answerSdp?.let {
                    signalingManager?.sendAnswer(it)
                }
            }

            override fun onAnswer(sdp: String) {
                webrtcManager?.setRemoteDescription(sdp, "answer")
            }

            override fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
                webrtcManager?.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
            }

            override fun onError(error: String) {
                showError("Ошибка: $error")
            }

            override fun onDisconnected() {
                updateStatus("Отключено от сервера")
            }
        })

        signalingManager?.connect()
    }

    private fun createOffer() {
        // Создаем offer
        val offer = webrtcManager?.createOffer()
        offer?.let {
            signalingManager?.sendOffer(it)
        }
    }

    private fun startTrafficStats() {
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                updateTrafficStats()
            }
        }
    }

    private fun updateTrafficStats() {
        val received = formatBytes(bytesReceived)
        val sent = formatBytes(bytesSent)
        trafficText.text = "⬇ $received  ⬆ $sent"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun updateStatus(status: String) {
        statusText.text = "Статус: $status"
    }

    private fun updateConnectButton(connected: Boolean) {
        connectButton.isEnabled = true
        connectButton.text = if (connected) "Отключить" else "Подключить"
        connectButton.backgroundTintList = getColorStateList(
            if (connected) R.color.disconnect_button else R.color.connect_button
        )
    }

    private fun showError(message: String) {
        statusText.text = "Ошибка: $message"
        vibrateError()
    }

    private fun vibrateSuccess() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    private fun vibrateError() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 100, 50, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectVPN()
    }

    companion object {
        // TURN credentials (получаются от сервера)
        private const val turnUsername = "turnuser"
        private const val turnPassword = "turnpass"
    }
}
