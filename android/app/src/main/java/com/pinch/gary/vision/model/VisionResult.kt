package com.pinch.gary.vision.model

/** One inference pass over a single decoded camera frame. */
data class VisionResult(
    val handLandmarks: List<HandLandmarks>,
    val detectedObjects: List<DetectedObject>,
    val frameTimestampMs: Long
)

/**
 * MediaPipe Hands' standard 21-point layout (index into [points]):
 * 0=wrist, 1-4=thumb (4=tip), 5-8=index (8=tip), 9-12=middle (12=tip),
 * 13-16=ring (16=tip), 17-20=pinky (20=tip). gesture/'s landmark classifier
 * depends on this exact ordering.
 */
data class HandLandmarks(
    val points: List<NormalizedLandmark>,
    val handedness: Handedness
)

data class NormalizedLandmark(val x: Float, val y: Float, val z: Float)

enum class Handedness { LEFT, RIGHT }

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox
)

/** Normalized 0-1 coordinates, image-relative. */
data class BoundingBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
