package com.pinch.gary.gesture.source

import com.pinch.gary.gesture.model.RawGestureEvent
import com.pinch.gary.vision.model.HandLandmarks

/**
 * Pure, stateful (across calls) heuristic engine — no coroutines, so it's
 * fully unit-testable with fabricated landmark sequences and zero mocking.
 * Called once per VisionResult sample (~15Hz) by CameraGestureSourceImpl.
 */
interface LandmarkGestureClassifier {
    fun classify(hands: List<HandLandmarks>, timestampMs: Long): List<RawGestureEvent>
}
