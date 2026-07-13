package com.pinch.gary.gesture.model

/**
 * The common currency every gesture/source/GestureSource implementation
 * emits — deliberately gesture/'s own type, not a reuse of
 * glasses/model/GlassesEvent.RadarGesture, so v0 (camera-only) stays fully
 * decoupled from glasses/. When radar hardware lands (ADR-010), a
 * RadarGestureSourceImpl maps GlassesEvent.RadarGesture into this same
 * type 1:1 — GestureRecognizerImpl's debounce/mapping logic never changes.
 */
enum class RawGestureType {
    PINCH,
    DOUBLE_PINCH,
    HOLD_PINCH,
    SWIPE_RIGHT,
    SWIPE_LEFT,
    SWIPE_UP,
    SWIPE_DOWN,
    AIR_TAP,
    GRAB,
    THUMB_SLIDE
}

data class RawGestureEvent(
    val type: RawGestureType,
    val timestampMs: Long,
    val magnitude: Float? = null
)
