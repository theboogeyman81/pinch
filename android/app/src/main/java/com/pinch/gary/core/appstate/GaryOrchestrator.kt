package com.pinch.gary.core.appstate

import com.pinch.gary.core.util.Logger
import com.pinch.gary.gesture.GestureRecognizer
import com.pinch.gary.glasses.GlassesManager
import com.pinch.gary.vision.VisionEngine
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GaryOrchestrator"

/**
 * The only class permitted to depend on more than one feature manager
 * (ADR-007). Cross-feature wiring lands here as each manager is built.
 *
 * week 3-4 (vision/, gesture/): vision/gesture v0 is camera-only per
 * ADR-010 — GestureRecognizer gets its input through gesture/'s own
 * GestureSource seam (CameraGestureSourceImpl depends on VisionEngine
 * internally), not through this orchestrator, so there's no glasses-event
 * routing to wire here yet. That lands when a RadarGestureSourceImpl is
 * added in a later hardware pass.
 *
 * - week 5-6 (voice/): route recognized gestures into VoiceSession.activate().
 * - week 7-8 (garyclient/): forward gesture/vision/context events over the
 *   cloud WebSocket, and dispatch cloud commands back to the right manager.
 */
@Singleton
class GaryOrchestrator @Inject constructor(
    private val glassesManager: GlassesManager,
    private val visionEngine: VisionEngine,
    private val gestureRecognizer: GestureRecognizer
) {
    fun start() {
        Logger.d(TAG, "GaryOrchestrator started (glasses/, vision/, gesture/ wired — gesture v0 is camera-only per ADR-010)")
        visionEngine.start()
        gestureRecognizer.start()
        // TODO(voice/, week 5-6): collect gestureRecognizer.commands here and route
        // Pinch/HoldPinch into VoiceSession.activate()/deactivate(); route
        // Swipe*/AirTap/Grab into whatever screen/mode is active.
        // AppState.lastGaryCommand already surfaces commands for now.
    }
}
