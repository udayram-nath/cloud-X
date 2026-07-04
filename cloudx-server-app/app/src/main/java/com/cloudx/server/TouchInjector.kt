package com.cloudx.server

import android.os.Build
import android.util.Log
import org.json.JSONObject

/**
 * Receives normalised touch events from the client via the WebRTC
 * data channel, de-normalises them to real screen coordinates,
 * and dispatches them via CloudXAccessibilityService.
 *
 * No root required — works via Android's AccessibilityService API.
 * The user must enable CloudX in Settings → Accessibility once.
 */
object TouchInjector {
    private const val TAG = "TouchInjector"

    var screenWidth: Int = 1080
    var screenHeight: Int = 2400

    fun inject(json: JSONObject) {
        val action = json.optString("action", "")
        val normX = json.optDouble("x", 0.0).toFloat()
        val normY = json.optDouble("y", 0.0).toFloat()

        // De-normalise to real pixel coordinates
        val x = (normX * screenWidth).toInt()
        val y = (normY * screenHeight).toInt()

        Log.d(TAG, "Touch $action at ($x, $y)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (CloudXAccessibilityService.isRunning()) {
                CloudXAccessibilityService.dispatchTouch(action, x, y)
            } else {
                Log.w(TAG, "AccessibilityService not enabled — " +
                    "go to Settings → Accessibility → CloudX → Enable")
            }
        } else {
            Log.w(TAG, "Touch injection needs Android 8.0+ (API 26) for continueStroke")
        }
    }
}
