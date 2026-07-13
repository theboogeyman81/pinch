package com.pinch.gary.gesture.source

import com.pinch.gary.gesture.model.RawGestureEvent
import com.pinch.gary.gesture.model.RawGestureType
import com.pinch.gary.vision.model.HandLandmarks
import com.pinch.gary.vision.model.NormalizedLandmark
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

// MediaPipe Hands' 21-point index convention (see vision/model/VisionResult.kt)
private const val WRIST = 0
private const val THUMB_TIP = 4
private const val INDEX_MCP = 5
private const val INDEX_TIP = 8
private const val MIDDLE_MCP = 9
private const val MIDDLE_TIP = 12
private const val RING_TIP = 16
private const val PINKY_TIP = 20

// All thresholds below are MVP heuristics — TODO: calibrate against real
// hand size/lighting on-device once a real hand can be tested against them.
private const val PINCH_THRESHOLD_RATIO = 0.35f
private const val THUMB_SLIDE_MIN_RATIO = 0.35f
private const val THUMB_SLIDE_MAX_RATIO = 0.55f
private const val GRAB_THRESHOLD_RATIO = 0.4f
private const val SWIPE_DISPLACEMENT_RATIO = 0.15f
private const val SWIPE_WINDOW_MS = 300L
private const val HOLD_PINCH_MS = 500L
private const val DOUBLE_PINCH_WINDOW_MS = 400L
private const val AIR_TAP_DEPTH_RATIO = 0.15f // z-depth vs. hand span — approximate, flagged low-confidence

@Singleton
class LandmarkGestureClassifierImpl @Inject constructor() : LandmarkGestureClassifier {

    private var pinchStartedAtMs: Long? = null
    private var holdFired = false
    private var pendingPinchReleaseAtMs: Long? = null
    private var lastThumbX: Float? = null
    private val centroidHistory = ArrayDeque<Pair<Long, Pair<Float, Float>>>()

    override fun classify(hands: List<HandLandmarks>, timestampMs: Long): List<RawGestureEvent> {
        val events = mutableListOf<RawGestureEvent>()

        // Deferred double-pinch resolution: a lone pinch-release isn't emitted
        // immediately — it waits out the double-pinch window in case a second
        // pinch arrives to upgrade it. If nothing arrives, fire it here.
        pendingPinchReleaseAtMs?.let { pendingAt ->
            if (timestampMs - pendingAt >= DOUBLE_PINCH_WINDOW_MS) {
                events += RawGestureEvent(RawGestureType.PINCH, pendingAt)
                pendingPinchReleaseAtMs = null
            }
        }

        val hand = hands.firstOrNull()
        if (hand == null) {
            lastThumbX = null
            centroidHistory.clear()
            return events
        }

        val points = hand.points
        if (points.size < 21) return events

        val handSpan = distance(points[WRIST], points[MIDDLE_MCP]).coerceAtLeast(1e-4f)
        val pinchDistance = distance(points[THUMB_TIP], points[INDEX_TIP]) / handSpan

        classifyPinch(pinchDistance, timestampMs, events)
        classifyThumbSlide(pinchDistance, points[THUMB_TIP].x, handSpan, timestampMs, events)
        classifyGrab(points, handSpan, timestampMs, events)
        classifyAirTap(points, handSpan, timestampMs, events)
        classifySwipe(points, handSpan, timestampMs, events)

        return events
    }

    private fun classifyPinch(pinchDistance: Float, timestampMs: Long, events: MutableList<RawGestureEvent>) {
        val isPinching = pinchDistance < PINCH_THRESHOLD_RATIO
        if (isPinching) {
            val startedAt = pinchStartedAtMs
            if (startedAt == null) {
                pinchStartedAtMs = timestampMs
                holdFired = false
            } else if (!holdFired && timestampMs - startedAt >= HOLD_PINCH_MS) {
                holdFired = true
                pendingPinchReleaseAtMs = null // a hold is never a tap; cancel any pending double-pinch window
                events += RawGestureEvent(RawGestureType.HOLD_PINCH, timestampMs)
            }
            return
        }

        if (pinchStartedAtMs != null && !holdFired) {
            val pendingAt = pendingPinchReleaseAtMs
            if (pendingAt != null && timestampMs - pendingAt < DOUBLE_PINCH_WINDOW_MS) {
                pendingPinchReleaseAtMs = null
                events += RawGestureEvent(RawGestureType.DOUBLE_PINCH, timestampMs)
            } else {
                pendingPinchReleaseAtMs = timestampMs
            }
        }
        pinchStartedAtMs = null
        holdFired = false
    }

    private fun classifyThumbSlide(
        pinchDistance: Float,
        thumbX: Float,
        handSpan: Float,
        timestampMs: Long,
        events: MutableList<RawGestureEvent>
    ) {
        if (pinchDistance !in THUMB_SLIDE_MIN_RATIO..THUMB_SLIDE_MAX_RATIO) {
            lastThumbX = null
            return
        }
        lastThumbX?.let { previous ->
            val delta = (thumbX - previous) / handSpan
            if (delta != 0f) {
                events += RawGestureEvent(RawGestureType.THUMB_SLIDE, timestampMs, delta)
            }
        }
        lastThumbX = thumbX
    }

    private fun classifyGrab(
        points: List<NormalizedLandmark>,
        handSpan: Float,
        timestampMs: Long,
        events: MutableList<RawGestureEvent>
    ) {
        val fingertips = listOf(THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP)
        val avgFingertipToWrist = fingertips.map { distance(points[it], points[WRIST]) }.average().toFloat() / handSpan
        if (avgFingertipToWrist < GRAB_THRESHOLD_RATIO) {
            events += RawGestureEvent(RawGestureType.GRAB, timestampMs)
        }
    }

    private fun classifyAirTap(
        points: List<NormalizedLandmark>,
        handSpan: Float,
        timestampMs: Long,
        events: MutableList<RawGestureEvent>
    ) {
        val depthRatio = abs(points[INDEX_TIP].z - points[WRIST].z) / handSpan
        if (depthRatio > AIR_TAP_DEPTH_RATIO) {
            events += RawGestureEvent(RawGestureType.AIR_TAP, timestampMs)
        }
    }

    private fun classifySwipe(
        points: List<NormalizedLandmark>,
        handSpan: Float,
        timestampMs: Long,
        events: MutableList<RawGestureEvent>
    ) {
        val centroidX = (points[WRIST].x + points[INDEX_MCP].x) / 2f
        val centroidY = (points[WRIST].y + points[INDEX_MCP].y) / 2f
        centroidHistory.addLast(timestampMs to (centroidX to centroidY))
        while (centroidHistory.isNotEmpty() && timestampMs - centroidHistory.first().first > SWIPE_WINDOW_MS) {
            centroidHistory.removeFirst()
        }
        val oldest = centroidHistory.firstOrNull() ?: return
        val dx = (centroidX - oldest.second.first) / handSpan
        val dy = (centroidY - oldest.second.second) / handSpan
        if (abs(dx) <= SWIPE_DISPLACEMENT_RATIO && abs(dy) <= SWIPE_DISPLACEMENT_RATIO) return

        val type = if (abs(dx) > abs(dy)) {
            if (dx > 0) RawGestureType.SWIPE_RIGHT else RawGestureType.SWIPE_LEFT
        } else {
            if (dy > 0) RawGestureType.SWIPE_DOWN else RawGestureType.SWIPE_UP
        }
        events += RawGestureEvent(type, timestampMs)
        centroidHistory.clear()
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
