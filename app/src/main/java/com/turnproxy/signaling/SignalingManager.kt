package com.turnproxy.signaling

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class AuthRequest(val type: String = "auth", val token: String)
data class AuthOkResponse(val type: String = "auth_ok", val session_id: String)
data class OfferMessage(val type: String = "offer", val sdp: String)
data class AnswerMessage(val type: String = "answer", val sdp: String)
data class CandidateMessage(
    val type: String = "candidate",
    val candidate: String,
    val sdp_mid: String,
    val sdp_mline_index: Int
)

class SignalingManager(
    private val serverUrl: String,
    private val token: String,
    private val listener: SignalingListener
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var sessionId: String? = null

    interface SignalingListener {
        fun onAuthenticated(sessionId: String)
        fun onOffer(sdp: String)
        fun onAnswer(sdp: String)
        fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int)
        fun onError(error: String)
        fun onDisconnected()
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                webSocket = ws
                listener.onError("WebSocket connected")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                listener.onError("WebSocket error: ${t.message}")
            }
        }

        webSocket = client.newWebSocket(request, wsListener)
        
        // Отправляем аутентификацию
        val authRequest = AuthRequest(token)
        val json = gson.toJson(authRequest)
        webSocket?.send(json)
        
        true
    }

    private fun handleMessage(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            when (type) {
                "auth_ok" -> {
                    sessionId = json.get("session_id")?.asString
                    sessionId?.let { listener.onAuthenticated(it) }
                }
                "offer" -> {
                    val sdp = json.get("sdp")?.asString ?: return
                    listener.onOffer(sdp)
                }
                "answer" -> {
                    val sdp = json.get("sdp")?.asString ?: return
                    listener.onAnswer(sdp)
                }
                "candidate" -> {
                    val candidate = json.get("candidate")?.asString ?: return
                    val sdpMid = json.get("sdp_mid")?.asString ?: ""
                    val sdpMLineIndex = json.get("sdp_mline_index")?.asInt ?: 0
                    listener.onIceCandidate(candidate, sdpMid, sdpMLineIndex)
                }
            }
        } catch (e: Exception) {
            listener.onError("Error handling message: ${e.message}")
        }
    }

    fun sendAnswer(sdp: String) {
        val answer = AnswerMessage(sdp = sdp)
        webSocket?.send(gson.toJson(answer))
    }

    fun sendIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val iceCandidate = CandidateMessage(
            candidate = candidate,
            sdp_mid = sdpMid,
            sdp_mline_index = sdpMLineIndex
        )
        webSocket?.send(gson.toJson(iceCandidate))
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
    }
}
