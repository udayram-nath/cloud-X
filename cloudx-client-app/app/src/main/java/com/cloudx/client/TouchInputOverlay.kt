package com.cloudx.client

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Transparent full-screen view layered on top of the video renderer.
 * Intercepts all touch events, normalises them to 0.0-1.0 coordinates
 * (so they work regardless of the server's actual screen size),
 * and sends them via the WebRTC data channel to the server.
 *
 * The server's TouchInjector replays these as real touch events
 * on the running game using Accessibility or ADB input.
 */
class TouchInputOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var webRtcClient: WebRtcClient? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val client = webRtcClient ?: return false

        val normX = event.x / width.toFloat()
        val normY = event.y / height.toFloat()

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "down"
            MotionEvent.ACTION_MOVE -> "move"
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> "up"
            else -> return false
        }

        client.sendTouchEvent(action, normX, normY)
        return true
    }
}
