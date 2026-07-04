package com.cloudx.server

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import org.webrtc.EglBase

class StreamingService : Service(), SignalingClient.Listener {

    companion object {
        const val CHANNEL_ID = "cloudx_streaming_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_SCREEN_WIDTH = "screen_width"
        const val EXTRA_SCREEN_HEIGHT = "screen_height"
        const val SIGNALING_SERVER_URL = "ws://192.168.1.100:8080"
    }

    private val TAG = "StreamingService"
    private lateinit var signalingClient: SignalingClient
    private lateinit var gameScanner: GameScanner
    private lateinit var eglBase: EglBase
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var projectionData: Intent? = null
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var activeWebRtcManager: WebRtcManager? = null
    private var activeSessionId: String? = null

    private val deviceId by lazy {
        android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    override fun onCreate() {
        super.onCreate()
        eglBase = EglBase.create()
        gameScanner = GameScanner(this)
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        screenWidth = intent?.getIntExtra(EXTRA_SCREEN_WIDTH, 1080) ?: 1080
        screenHeight = intent?.getIntExtra(EXTRA_SCREEN_HEIGHT, 2400) ?: 2400

        TouchInjector.screenWidth = screenWidth
        TouchInjector.screenHeight = screenHeight

        startForegroundWithNotification()

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val password = ServerPrefs.getPassword(this)

        signalingClient = SignalingClient(
            serverUrl = SIGNALING_SERVER_URL,
            deviceId = deviceId,
            deviceName = deviceName,
            password = password,
            listener = this
        )

        val games = gameScanner.scanInstalledGames()
        signalingClient.connect(games, getBatteryPercent())

        return START_STICKY
    }

    private fun getBatteryPercent(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "CloudX Streaming", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CloudX Server Running")
            .setContentText("Ready to stream games to your devices")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onRegistered() {
        Log.i(TAG, "Registered with signaling server as $deviceId")
    }

    override fun onIncomingConnection(sessionId: String, passwordAttemptHash: String) {
        val accepted = signalingClient.verifyPassword(passwordAttemptHash)
        signalingClient.respondToConnection(sessionId, accepted)
        if (accepted) activeSessionId = sessionId
    }

    override fun onWebRtcOffer(sessionId: String, sdp: String) {
        if (sessionId != activeSessionId) return
        val pData = projectionData ?: return

        activeWebRtcManager = WebRtcManager(
            mediaProjectionManager = mediaProjectionManager!!,
            mediaProjectionData = pData,
            eglBase = eglBase,
            signalingClient = signalingClient,
            sessionId = sessionId
        )
        activeWebRtcManager?.start(screenWidth, screenHeight)
        activeWebRtcManager?.handleRemoteOffer(sdp)
    }

    override fun onIceCandidate(sessionId: String, candidate: JSONObject) {
        if (sessionId == activeSessionId) activeWebRtcManager?.addRemoteIceCandidate(candidate)
    }

    override fun onLaunchGame(sessionId: String, gameId: String) {
        if (sessionId != activeSessionId) return
        val launchIntent = packageManager.getLaunchIntentForPackage(gameId) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    override fun onSessionEnded(sessionId: String) {
        if (sessionId == activeSessionId) {
            activeWebRtcManager?.stop()
            activeWebRtcManager = null
            activeSessionId = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeWebRtcManager?.stop()
        signalingClient.disconnect()
        eglBase.release()
        super.onDestroy()
    }
}
