package com.cloudx.server

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.util.LinkedList
import kotlin.math.sqrt

/**
 * AccessibilityService that receives de-normalised touch coordinates
 * from TouchInjector and replays them as real gestures inside whatever
 * game is running on screen — no root required.
 *
 * Setup (one-time, done by the user):
 *   Settings → Accessibility → Installed Services → CloudX → Enable
 *
 * How it works:
 *   Client sends touch JSON → WebRTC data channel →
 *   WebRtcManager.onMessage → TouchInjector.inject() →
 *   CloudXAccessibilityService.dispatchTouch() →
 *   dispatchGesture() → game receives real touch
 *
 * v2 fixes:
 *  - down no longer fires an immediate phantom tap; tap vs. drag is
 *    decided once we know whether real movement happened.
 *  - drag/swipe is now a genuinely continuous stroke, built by chaining
 *    GestureDescription.StrokeDescription.continueStroke() segment by
 *    segment (each new segment is only dispatched after the previous one
 *    completes), instead of re-dispatching the whole path from scratch
 *    on every move event.
 *  - all gesture state and dispatchGesture() calls now happen on the
 *    service's main thread via a Handler, since touch events arrive on
 *    a WebRTC data-channel thread, not the main thread.
 *
 * Known limitation: some competitive mobile games (anti-cheat systems in
 * titles like COD Mobile / PUBG Mobile / Free Fire) actively detect and
 * discard AccessibilityService-dispatched touches. Test against your
 * actual target game early — this plumbing being correct doesn't
 * guarantee the game will accept the input.
 */
@RequiresApi(Build.VERSION_CODES.O)
class CloudXAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CloudXA11y"

        // Singleton reference so TouchInjector can call us statically
        @Volatile
        private var instance: CloudXAccessibilityService? = null

        private const val SEGMENT_DURATION_MS = 30L   // short segments = responsive dragging
        private const val TAP_DURATION_MS = 50L
        private const val TAP_MOVE_THRESHOLD_PX = 12f // finger jitter under this = still a tap

        /**
         * Called by TouchInjector.inject() for every incoming touch event.
         * action: "down", "move", or "up"
         * x, y: real pixel coordinates (already de-normalised by TouchInjector)
         */
        fun dispatchTouch(action: String, x: Int, y: Int) {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "Service not running — enable CloudX in Accessibility settings")
                return
            }
            // Touch events arrive on a WebRTC data-channel thread. Gesture
            // state and dispatchGesture() must be touched from the main
            // thread, so hop over before doing anything else.
            svc.mainHandler.post { svc.handleTouch(action, x, y) }
        }

        fun isRunning() = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- Stroke/drag state — only ever touched on the main thread ----
    private var downPoint: PointF? = null
    private var lastPoint: PointF? = null
    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var dispatchInFlight = false
    private var strokeEndedByUp = false
    private val pendingQueue: LinkedList<PointF> = LinkedList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "CloudX AccessibilityService connected ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to inspect UI events — we only inject gestures
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ---- Touch state machine (main thread only) ----

    private fun handleTouch(action: String, x: Int, y: Int) {
        val point = PointF(x.toFloat(), y.toFloat())

        when (action) {
            "down" -> {
                downPoint = point
                lastPoint = point
                lastStroke = null
                dispatchInFlight = false
                strokeEndedByUp = false
                pendingQueue.clear()
                // Don't dispatch anything yet — a down+up with no real
                // movement in between is a tap, decided entirely at "up".
            }

            "move" -> {
                val from = lastPoint ?: point
                val start = downPoint
                lastPoint = point

                if (start != null && lastStroke == null && !dispatchInFlight &&
                    distance(start, point) < TAP_MOVE_THRESHOLD_PX
                ) {
                    // Still within finger-jitter tolerance — don't start a
                    // drag gesture yet, might still turn out to be a tap.
                    return
                }

                if (dispatchInFlight) {
                    // A segment is already mid-flight — queue this point so
                    // the completion callback can continue the same stroke,
                    // instead of firing an overlapping gesture on top of it.
                    pendingQueue.add(point)
                } else {
                    dispatchSegment(from, point, willContinue = true)
                }
            }

            "up" -> {
                val start = downPoint
                val from = lastPoint ?: point

                val wasJustATap = start != null &&
                    lastStroke == null &&
                    !dispatchInFlight &&
                    distance(start, point) < TAP_MOVE_THRESHOLD_PX

                if (wasJustATap) {
                    dispatchTap(point)
                } else {
                    strokeEndedByUp = true
                    if (dispatchInFlight) {
                        pendingQueue.add(point)
                    } else {
                        dispatchSegment(from, point, willContinue = false)
                    }
                }
                downPoint = null
            }
        }
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Dispatches one segment of a (possibly multi-segment) continuous
     * stroke. If a previous segment exists, this one continues it via
     * continueStroke() rather than starting an unrelated gesture.
     */
    private fun dispatchSegment(from: PointF, to: PointF, willContinue: Boolean) {
        val path = Path().apply {
            moveTo(from.x, from.y)
            lineTo(to.x, to.y)
        }

        val stroke = try {
            lastStroke?.continueStroke(path, 0, SEGMENT_DURATION_MS, willContinue)
                ?: GestureDescription.StrokeDescription(path, 0, SEGMENT_DURATION_MS, willContinue)
        } catch (e: IllegalStateException) {
            // Previous stroke wasn't in a continuable state (e.g. cancelled
            // by the system) — start a fresh stroke instead of crashing.
            Log.w(TAG, "continueStroke failed, starting a new stroke: ${e.message}")
            GestureDescription.StrokeDescription(path, 0, SEGMENT_DURATION_MS, willContinue)
        }

        lastStroke = stroke
        dispatchInFlight = true

        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onSegmentFinished()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture segment cancelled")
                lastStroke = null
                onSegmentFinished()
            }
        }, mainHandler)
    }

    /** Runs on the main thread after a segment's dispatchGesture callback fires. */
    private fun onSegmentFinished() {
        dispatchInFlight = false
        val next = pendingQueue.poll()

        if (next != null) {
            val from = lastPoint ?: next
            val isFinalSegment = strokeEndedByUp && pendingQueue.isEmpty()
            dispatchSegment(from, next, willContinue = !isFinalSegment)
            if (isFinalSegment) {
                lastStroke = null
                strokeEndedByUp = false
            }
        } else if (strokeEndedByUp) {
            lastStroke = null
            strokeEndedByUp = false
        }
    }

    private fun dispatchTap(point: PointF) {
        val path = Path().apply { moveTo(point.x, point.y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.v(TAG, "Tap dispatched at (${point.x}, ${point.y})")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled at (${point.x}, ${point.y})")
            }
        }, mainHandler)
    }
}
