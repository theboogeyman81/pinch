package com.pinch.gary.core.appstate

import com.pinch.gary.core.util.Logger
import com.pinch.gary.glasses.GlassesManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GaryOrchestrator"

/**
 * The only class permitted to depend on more than one feature manager
 * (ADR-007). Cross-feature wiring lands here as each manager is built:
 *
 * - week 3-4 (vision/, gesture/): route [GlassesManager] frame/event streams
 *   into VisionEngine and GestureRecognizer.
 * - week 5-6 (voice/): route recognized gestures into VoiceSession.activate().
 * - week 7-8 (garyclient/): forward gesture/vision/context events over the
 *   cloud WebSocket, and dispatch cloud commands back to the right manager.
 *
 * Right now there is exactly one manager, so there is nothing to orchestrate
 * yet — this class exists as the landing spot rather than being invented
 * later, so no future PR has to introduce the "who's allowed to see two
 * managers" rule from scratch.
 */
@Singleton
class GaryOrchestrator @Inject constructor(
    private val glassesManager: GlassesManager
) {
    fun start() {
        Logger.d(TAG, "GaryOrchestrator started (glasses/ only, no cross-feature wiring yet)")
    }
}
