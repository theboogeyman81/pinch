package com.pinch.gary.glasses.model

/** Identity + last-known telemetry for a paired pair of glasses. */
data class GlassesDevice(
    val address: String,
    val name: String?,
    val batteryPercent: Int? = null
)

sealed interface GlassesEvent {
    data class TouchTap(val timestampMs: Long) : GlassesEvent
    data class BatteryUpdate(val percent: Int) : GlassesEvent
    data class RadarGesture(val gesture: RadarGestureType) : GlassesEvent
}

enum class RadarGestureType {
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
