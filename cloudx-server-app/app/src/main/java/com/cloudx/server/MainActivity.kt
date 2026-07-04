package com.cloudx.server

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Setup screen: user sets a device name + password, then taps "Start Server".
 * Android requires MediaProjection permission to be granted from an Activity
 * (a system "this app will start capturing your screen" dialog) — that's
 * what startActivityForResult below triggers. Once granted, we hand the
 * resulting Intent off to StreamingService and the Activity can be closed;
 * the service keeps running in the background.
 */
class MainActivity : AppCompatActivity() {

    private val PROJECTION_REQUEST_CODE = 1001
    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val startButton = findViewById<Button>(R.id.startButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        passwordInput.setText(ServerPrefs.getPassword(this))

        startButton.setOnClickListener {
            val password = passwordInput.text.toString().ifBlank { "cloudx123" }
            ServerPrefs.setPassword(this, password)
            statusText.text = "Requesting screen capture permission..."

            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            startActivityForResult(captureIntent, PROJECTION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PROJECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)

                val serviceIntent = Intent(this, StreamingService::class.java).apply {
                    putExtra(StreamingService.EXTRA_PROJECTION_DATA, data)
                    putExtra(StreamingService.EXTRA_SCREEN_WIDTH, metrics.widthPixels)
                    putExtra(StreamingService.EXTRA_SCREEN_HEIGHT, metrics.heightPixels)
                }
                startForegroundService(serviceIntent)

                findViewById<TextView>(R.id.statusText).text =
                    "CloudX Server running. You can close this screen — keep the app in background."
            } else {
                findViewById<TextView>(R.id.statusText).text =
                    "Screen capture permission denied. CloudX needs this to stream games."
            }
        }
    }
}
