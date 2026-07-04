package com.cloudx.client

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

/**
 * Full screen streaming player.
 * ✅ Bug 2 fix: registers itself as the signaling listener on onCreate
 * so WebRTC answer and ICE candidates actually reach webRtcClient.
 */
class StreamActivity : AppCompatActivity(), SignalingClient.Listener {

    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var touchOverlay: TouchInputOverlay
    private lateinit var backButton: ImageButton
    private lateinit var eglBase: EglBase
    private lateinit var webRtcClient: WebRtcClient

    private val sessionId get() = ClientState.sessionId
    private val signalingClient get() = ClientState.signalingClient!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_stream)

        renderer = findViewById(R.id.streamRenderer)
        touchOverlay = findViewById(R.id.touchOverlay)
        backButton = findViewById(R.id.backButton)

        eglBase = EglBase.create()

        // ✅ Bug 2 fix: redirect signaling events to this activity
        // so the WebRTC answer and ICE candidates are no longer swallowed
        signalingClient.listener = this

        webRtcClient = WebRtcClient(
            eglBase = eglBase,
            signalingClient = signalingClient,
            sessionId = sessionId,
            renderer = renderer
        )
        touchOverlay.webRtcClient = webRtcClient
        webRtcClient.start()

        // Long press to toggle back button
        touchOverlay.setOnLongClickListener {
            backButton.visibility =
                if (backButton.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            true
        }

        backButton.setOnClickListener {
            signalingClient.endSession(sessionId)
            finish()
        }
    }

    // ✅ These now actually fire because listener was re-pointed above
    override fun onWebRtcAnswer(sessionId: String, sdp: String) {
        webRtcClient.handleRemoteAnswer(sdp)
    }

    override fun onIceCandidate(sessionId: String, candidate: JSONObject) {
        webRtcClient.addRemoteIceCandidate(candidate)
    }

    override fun onSessionEnded(sessionId: String) {
        runOnUiThread { finish() }
    }

    override fun onHostListUpdated(hosts: List<HostDevice>) {}
    override fun onConnectionAccepted(sessionId: String, games: List<GameItem>) {}
    override fun onConnectionRejected(reason: String) {}
    override fun onDisconnected() { runOnUiThread { finish() } }

    override fun onDestroy() {
        webRtcClient.stop()
        eglBase.release()
        super.onDestroy()
    }
}
