package com.cloudx.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * First screen. User enters:
 *  - Signaling server URL (e.g. ws://192.168.1.100:8080 on LAN,
 *    or ws://<tailscale-ip>:8080 for remote access from college)
 *  - Password they set on the Server App
 *
 * On connect, we open the signaling WebSocket, wait for the host list,
 * then pass everything to GameLibraryActivity.
 */
class LoginActivity : AppCompatActivity(), SignalingClient.Listener {

    private lateinit var signalingClient: SignalingClient
    private lateinit var serverUrlInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private var selectedHostId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        passwordInput = findViewById(R.id.passwordInput)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)

        // Pre-fill last used values
        val prefs = getSharedPreferences("cloudx_client", MODE_PRIVATE)
        serverUrlInput.setText(prefs.getString("server_url", "ws://192.168.1.100:8080"))

        connectButton.setOnClickListener {
            val url = serverUrlInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (url.isBlank()) {
                Toast.makeText(this, "Enter server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("server_url", url).apply()
            statusText.text = "Connecting to server..."
            connectButton.isEnabled = false

            signalingClient = SignalingClient(url, this)
            signalingClient.connect()

            // Store password for use after host list arrives
            ClientState.password = password
            ClientState.signalingClient = signalingClient
        }
    }

    // ---------- SignalingClient.Listener ----------

    override fun onHostListUpdated(hosts: List<HostDevice>) {
        runOnUiThread {
            if (hosts.isEmpty()) {
                statusText.text = "Connected. Waiting for host device to come online..."
                return@runOnUiThread
            }
            // For v1: auto-connect to the first (and likely only) host
            val host = hosts.first()
            statusText.text = "Found: ${host.deviceName} — connecting..."
            signalingClient.requestConnection(host.deviceId, ClientState.password)
            selectedHostId = host.deviceId
        }
    }

    override fun onConnectionAccepted(sessionId: String, games: List<GameItem>) {
        runOnUiThread {
            ClientState.sessionId = sessionId
            ClientState.games = games
            val intent = Intent(this, GameLibraryActivity::class.java)
            startActivity(intent)
            // Don't finish() — keep SignalingClient alive via ClientState
        }
    }

    override fun onConnectionRejected(reason: String) {
        runOnUiThread {
            statusText.text = "Connection rejected: $reason"
            connectButton.isEnabled = true
        }
    }

    override fun onWebRtcAnswer(sessionId: String, sdp: String) {
        // Handled by StreamActivity
    }

    override fun onIceCandidate(sessionId: String, candidate: org.json.JSONObject) {
        // Handled by StreamActivity
    }

    override fun onSessionEnded(sessionId: String) {
        runOnUiThread {
            statusText.text = "Session ended. Tap connect to reconnect."
            connectButton.isEnabled = true
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            statusText.text = "Disconnected from server. Check URL and try again."
            connectButton.isEnabled = true
        }
    }
}
