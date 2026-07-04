package com.cloudx.server

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import org.webrtc.*

/**
 * Handles screen capture + WebRTC streaming for ONE active session.
 * All WebRTC classes are from org.webrtc.* (the real package exposed
 * by stream-webrtc-android and all other WebRTC Android builds).
 */
class WebRtcManager(
    private val mediaProjectionManager: MediaProjectionManager,
    private val mediaProjectionData: Intent,
    private val eglBase: EglBase,
    private val signalingClient: SignalingClient,
    private val sessionId: String
) {
    private val TAG = "WebRtcManager"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    fun start(screenWidth: Int, screenHeight: Int, fps: Int = 60) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(null)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    val json = org.json.JSONObject().apply {
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                        put("candidate", candidate.sdp)
                    }
                    signalingClient.sendIceCandidate(sessionId, json)
                }
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.i(TAG, "Connection state: $newState")
                }
                override fun onDataChannel(channel: DataChannel?) {
                    Log.i(TAG, "Data channel: ${channel?.label()}")
                    channel?.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(p0: Long) {}
                        override fun onStateChange() {}
                        override fun onMessage(buffer: DataChannel.Buffer?) {
                            // Touch input from client — handled by TouchInjector
                            buffer ?: return
                            val bytes = ByteArray(buffer.data.remaining())
                            buffer.data.get(bytes)
                            val json = org.json.JSONObject(String(bytes))
                            TouchInjector.inject(json)
                        }
                    })
                }
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            }
        )

        startScreenCapture(screenWidth, screenHeight, fps)
    }

    private fun startScreenCapture(width: Int, height: Int, fps: Int) {
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoSource = peerConnectionFactory?.createVideoSource(true)

        screenCapturer = ScreenCapturerAndroid(
            mediaProjectionData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "Screen capture stopped")
                }
            }
        )
        screenCapturer?.initialize(surfaceTextureHelper, null, videoSource?.capturerObserver)
        screenCapturer?.startCapture(width, height, fps)

        videoTrack = peerConnectionFactory?.createVideoTrack("cloudx-screen", videoSource)
        videoTrack?.setEnabled(true)

        val stream = peerConnectionFactory?.createLocalMediaStream("cloudx-stream")
        stream?.addTrack(videoTrack)
        peerConnection?.addStream(stream)
    }

    fun handleRemoteOffer(sdp: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { createAnswer() }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) { Log.e(TAG, "setRemoteDesc failed: $p0") }
        }, offer)
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { signalingClient.sendAnswer(sessionId, desc.description) }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) { Log.e(TAG, "setLocalDesc failed: $p0") }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "createAnswer failed: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun addRemoteIceCandidate(candidateJson: org.json.JSONObject) {
        peerConnection?.addIceCandidate(
            IceCandidate(
                candidateJson.getString("sdpMid"),
                candidateJson.getInt("sdpMLineIndex"),
                candidateJson.getString("candidate")
            )
        )
    }

    fun stop() {
        screenCapturer?.stopCapture()
        screenCapturer?.dispose()
        videoTrack?.dispose()
        videoSource?.dispose()
        peerConnection?.close()
        surfaceTextureHelper?.dispose()
    }
}
