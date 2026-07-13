package com.pinch.gary.vision.camera

import androidx.lifecycle.LifecycleOwner

/**
 * v0 substitute for the ESP32 camera (ADR-010) — pushes JPEG frames from the
 * phone's own camera into the same FrameRingBuffer the glasses MJPEG stream
 * will use once hardware exists. Bind/unbind are lifecycle-scoped so capture
 * only runs while the app is foregrounded — no background camera use.
 */
interface PhoneCameraSource {
    fun bind(lifecycleOwner: LifecycleOwner)
    fun unbind()
}
