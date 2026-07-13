package com.pinch.gary.vision

import com.pinch.gary.vision.model.VisionResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface boundary around MediaPipe/TFLite inference, per ADR-008 — lets
 * AppState/tests depend on a fake instead of real Android ML APIs.
 */
interface VisionEngine {
    val visionResult: StateFlow<VisionResult?>
    val isRunning: StateFlow<Boolean>

    fun start()
    fun stop()
}
