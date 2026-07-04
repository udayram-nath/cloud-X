package com.cloudx.server

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/**
 * Connects this device (the "Server App") to the CloudX signaling server.
 * Handles registration, incoming connection requests, and relays
 * WebRTC SDP/ICE messages to WebRtcManager.
 */
class SignalingClient(
    serverUrl: String,
    private val deviceId: String,
    private val deviceName: String,
    private val password: String,
    private val listener: Listener
) {
    interface Listener {
        fun onRegistered()
        fun onIncomingConnection(sessionId: String, passwordAttemptHash: String)
        fun onWebRtcOffer(sessionId: String, sdp: String)
        fun onIceCandidate(sessionId: String, candidate: JSONObject)
        fun onLaunchGame(sessionId: String, gameId: String)
        fun onSessionEnded(sessionId: String)
    }

    private val TAG = "SignalingClient"
    private var ws: WebSocketClient? = null

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun connect(games: List<GameEntry>, batteryPercent: Int) {
        ws = object : WebSocketClient(URI(serverUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.i(TAG, "Connected to signaling server")
                val msg = JSONObject().apply {
                    put("type", "register_host")
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    put("passwordHash", sha256(password))
                }
                val gamesArr = org.json.JSONArray()
                games.forEach {
                    gamesArr.put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                    })
                }
                msg.put("games", gamesArr)
                msg.put("battery", batteryPercent)
                send(msg.toString())
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                handleMessage(message)
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w(TAG, "Signaling connection closed: $reason. Will need reconnect logic.")
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "Signaling error", ex)
            }
        }
        ws?.connect()
    }

    private fun handleMessage(raw: String) {
        val msg = try { JSONObject(raw) } catch (e: Exception) { return }
        when (msg.optString("type")) {
            "registered" -> listener.onRegistered()

            "incoming_connection" -> listener.onIncomingConnection(
                msg.getString("sessionId"),
                msg.getString("passwordAttemptHash")
            )

            "webrtc_offer" -> {
                val payload = msg.getJSONObject("payload")
                listener.onWebRtcOffer(msg.getString("sessionId"), payload.getString("sdp"))
            }

            "webrtc_ice_candidate" -> {
                listener.onIceCandidate(msg.getString("sessionId"), msg.getJSONObject("payload"))
            }

            "launch_game" -> listener.onLaunchGame(
                msg.getString("sessionId"),
                msg.getString("gameId")
            )

            "session_ended" -> listener.onSessionEnded(msg.getString("sessionId"))
        }
    }

    fun respondToConnection(sessionId: String, accepted: Boolean) {
        val msg = JSONObject().apply {
            put("type", "connection_decision")
            put("sessionId", sessionId)
            put("accepted", accepted)
        }
        ws?.send(msg.toString())
    }

    fun sendAnswer(sessionId: String, sdp: String) {
        val msg = JSONObject().apply {
            put("type", "webrtc_answer")
            put("sessionId", sessionId)
            put("payload", JSONObject().put("sdp", sdp))
        }
        ws?.send(msg.toString())
    }

    fun sendIceCandidate(sessionId: String, candidate: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "webrtc_ice_candidate")
            put("sessionId", sessionId)
            put("payload", candidate)
        }
        ws?.send(msg.toString())
    }

    fun verifyPassword(attemptHash: String): Boolean {
        return attemptHash == sha256(password)
    }

    fun disconnect() {
        ws?.close()
    }
}
