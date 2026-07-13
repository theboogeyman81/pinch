package com.pinch.gary.gesture.source

import com.pinch.gary.core.di.ApplicationScope
import com.pinch.gary.gesture.model.RawGestureEvent
import com.pinch.gary.vision.VisionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0 GestureSource (ADR-010) — thin reactive adapter that depends on exactly
 * one manager (VisionEngine, ADR-007-compliant) and delegates the actual
 * landmark heuristics to LandmarkGestureClassifier. A future
 * RadarGestureSourceImpl (depending on GlassesManager) is the only other
 * class that would ever need to change when radar hardware lands.
 */
@Singleton
class CameraGestureSourceImpl @Inject constructor(
    private val visionEngine: VisionEngine,
    private val classifier: LandmarkGestureClassifier,
    @ApplicationScope private val scope: CoroutineScope
) : GestureSource {

    private val _events = MutableSharedFlow<RawGestureEvent>(extraBufferCapacity = 16)
    override val events: Flow<RawGestureEvent> = _events.asSharedFlow()

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            visionEngine.visionResult.filterNotNull().collect { result ->
                classifier.classify(result.handLandmarks, result.frameTimestampMs).forEach { event ->
                    _events.tryEmit(event)
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}
