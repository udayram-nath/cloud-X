package com.cloudx.client

import android.util.Log
import org.json.JSONObject
import org.webrtc.*

/**
 * Client-side WebRTC manager.
 * All imports from org.webrtc.* — the real package in stream-webrtc-android.
 */
class WebRtcClient(
    private val eglBase: EglBase,
    private val signalingClient: SignalingClient,
    private val sessionId: String,
    private val renderer: SurfaceViewRenderer
) {
    private val TAG = "WebRtcClient"
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    fun start() {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(false)
        renderer.setEnableHardwareScaler(true)

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(null)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        peerConnection = peerConnectionFactory?.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers),
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    val json = JSONObject().apply {
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                        put("candidate", candidate.sdp)
                    }
                    signalingClient.sendIceCandidate(sessionId, json)
                }

                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        Log.i(TAG, "Video track received — attaching to renderer")
                        track.addSink(renderer)
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.i(TAG, "Connection state: $newState")
                }

                override fun onDataChannel(dc: DataChannel?) {
                    Log.i(TAG, "Data channel: ${dc?.label()}")
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            }
        )

        // Data channel for sending touch events back to server
        dataChannel = peerConnection?.createDataChannel(
            "touch-input",
            DataChannel.Init().apply { ordered = true }
        )

        createAndSendOffer()
    }

    private fun createAndSendOffer() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        signalingClient.sendOffer(sessionId, desc.description)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) { Log.e(TAG, "setLocalDesc failed: $p0") }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "createOffer failed: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun handleRemoteAnswer(sdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { Log.i(TAG, "Remote answer set — stream starting") }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) { Log.e(TAG, "setRemoteDesc failed: $p0") }
        }, answer)
    }

    fun addRemoteIceCandidate(candidateJson: JSONObject) {
        peerConnection?.addIceCandidate(
            IceCandidate(
                candidateJson.getString("sdpMid"),
                candidateJson.getInt("sdpMLineIndex"),
                candidateJson.getString("candidate")
            )
        )
    }

    fun sendTouchEvent(action: String, normX: Float, normY: Float) {
        val dc = dataChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        val json = JSONObject().apply {
            put("action", action)
            put("x", normX)
            put("y", normY)
        }.toString()
        dc.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(json.toByteArray()), false))
    }

    fun stop() {
        dataChannel?.close()
        peerConnection?.close()
        renderer.release()
        peerConnectionFactory?.dispose()
    }
}
