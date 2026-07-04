package com.cloudx.client

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

data class HostDevice(
    val deviceId: String,
    val deviceName: String,
    val gameCount: Int,
    val battery: Int,
    val online: Boolean
)

data class GameItem(
    val id: String,
    val name: String
)

class SignalingClient(
    private val serverUrl: String,
    initialListener: Listener
) {
    interface Listener {
        fun onHostListUpdated(hosts: List<HostDevice>)
        fun onConnectionAccepted(sessionId: String, games: List<GameItem>)
        fun onConnectionRejected(reason: String)
        fun onWebRtcAnswer(sessionId: String, sdp: String)
        fun onIceCandidate(sessionId: String, candidate: JSONObject)
        fun onSessionEnded(sessionId: String)
        fun onDisconnected()
    }

    private val TAG = "ClientSignaling"
    private var ws: WebSocketClient? = null
    private val clientId = "client-${System.currentTimeMillis()}"

    // ✅ Bug 2 fix: mutable listener so StreamActivity can swap in
    var listener: Listener = initialListener

    fun connect() {
        ws = object : WebSocketClient(URI(serverUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.i(TAG, "Connected to signaling server")
                send(JSONObject().apply {
                    put("type", "register_client")
                    put("clientId", clientId)
                }.toString())
            }

            override fun onMessage(message: String?) {
                if (message != null) handleMessage(message)
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w(TAG, "Disconnected: $reason")
                listener.onDisconnected()
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "Signaling error", ex)
                listener.onDisconnected()
            }
        }
        ws?.connect()
    }

    private fun handleMessage(raw: String) {
        val msg = try { JSONObject(raw) } catch (e: Exception) { return }

        when (msg.optString("type")) {
            "host_list" -> {
                val hostsArr = msg.getJSONArray("hosts")
                val hosts = (0 until hostsArr.length()).map {
                    val h = hostsArr.getJSONObject(it)
                    HostDevice(
                        deviceId = h.getString("deviceId"),
                        deviceName = h.getString("deviceName"),
                        gameCount = h.optInt("gameCount", 0),
                        battery = h.optInt("battery", -1),
                        online = h.optBoolean("online", true)
                    )
                }
                listener.onHostListUpdated(hosts)
            }

            "connection_accepted" -> {
                val sessionId = msg.getString("sessionId")
                val gamesArr = msg.getJSONArray("games")
                val games = (0 until gamesArr.length()).map {
                    val g = gamesArr.getJSONObject(it)
                    GameItem(g.getString("id"), g.getString("name"))
                }
                listener.onConnectionAccepted(sessionId, games)
            }

            "connection_rejected" ->
                listener.onConnectionRejected(msg.optString("reason", "Rejected"))

            "webrtc_answer" -> {
                val payload = msg.getJSONObject("payload")
                listener.onWebRtcAnswer(msg.getString("sessionId"), payload.getString("sdp"))
            }

            "webrtc_ice_candidate" ->
                listener.onIceCandidate(msg.getString("sessionId"), msg.getJSONObject("payload"))

            "session_ended" ->
                listener.onSessionEnded(msg.getString("sessionId"))
        }
    }

    fun requestConnection(deviceId: String, password: String) {
        ws?.send(JSONObject().apply {
            put("type", "connect_request")
            put("deviceId", deviceId)
            put("passwordHash", sha256(password))
        }.toString())
    }

    fun sendOffer(sessionId: String, sdp: String) {
        ws?.send(JSONObject().apply {
            put("type", "webrtc_offer")
            put("sessionId", sessionId)
            put("payload", JSONObject().put("sdp", sdp))
        }.toString())
    }

    fun sendIceCandidate(sessionId: String, candidate: JSONObject) {
        ws?.send(JSONObject().apply {
            put("type", "webrtc_ice_candidate")
            put("sessionId", sessionId)
            put("payload", candidate)
        }.toString())
    }

    fun launchGame(sessionId: String, gameId: String) {
        ws?.send(JSONObject().apply {
            put("type", "launch_game")
            put("sessionId", sessionId)
            put("gameId", gameId)
        }.toString())
    }

    fun endSession(sessionId: String) {
        ws?.send(JSONObject().apply {
            put("type", "end_session")
            put("sessionId", sessionId)
        }.toString())
    }

    fun disconnect() { ws?.close() }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
