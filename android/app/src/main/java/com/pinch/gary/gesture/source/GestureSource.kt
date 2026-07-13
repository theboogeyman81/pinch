package com.pinch.gary.gesture.source

import com.pinch.gary.gesture.model.RawGestureEvent
import kotlinx.coroutines.flow.Flow

/**
 * Per ADR-010: the seam that lets radar become a second implementation
 * later (RadarGestureSourceImpl, depending on GlassesManager) without
 * touching GestureRecognizerImpl's command-mapping/debounce logic — only
 * GestureModule's @Binds line changes to switch. v0's only implementation
 * is CameraGestureSourceImpl, which adapts VisionEngine's hand landmarks.
 */
interface GestureSource {
    val events: Flow<RawGestureEvent>

    fun start()
    fun stop()
}
