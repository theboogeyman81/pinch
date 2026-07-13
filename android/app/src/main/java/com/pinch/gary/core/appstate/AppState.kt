package com.pinch.gary.core.appstate

import com.pinch.gary.core.di.ApplicationScope
import com.pinch.gary.gesture.GestureRecognizer
import com.pinch.gary.gesture.model.GaryCommand
import com.pinch.gary.glasses.GlassesManager
import com.pinch.gary.glasses.model.GlassesConnectionState
import com.pinch.gary.vision.VisionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for screen-level UI state — see ADR-007.
 *
 * The full VisionResult stream (21 landmarks/hand at ~15Hz) deliberately
 * does NOT live here — nothing in the current UI needs per-landmark detail,
 * and 15Hz recomposition of screen state would be wasted Compose diffing.
 * Only two cheap, human-rate signals are surfaced; anything needing the raw
 * stream reads VisionEngine/GestureRecognizer directly.
 */
data class AppUiState(
    val glassesConnectionState: GlassesConnectionState = GlassesConnectionState.Disconnected,
    val isVisionEngineRunning: Boolean = false,
    val lastGaryCommand: GaryCommand? = null
)

/**
 * Combines every feature manager's [StateFlow] into one [AppUiState]. Add a
 * manager to the `combine()` here the same week its package is scaffolded
 * (voice/etc.), never before.
 *
 * Screen ViewModels depend on this class only, never on individual managers.
 */
@Singleton
class AppState @Inject constructor(
    glassesManager: GlassesManager,
    visionEngine: VisionEngine,
    gestureRecognizer: GestureRecognizer,
    @ApplicationScope scope: CoroutineScope
) {
    private val lastGaryCommand: StateFlow<GaryCommand?> =
        gestureRecognizer.commands.stateIn(scope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AppUiState> = combine(
        glassesManager.connectionState,
        visionEngine.isRunning,
        lastGaryCommand
    ) { connectionState, visionRunning, garyCommand ->
        AppUiState(connectionState, visionRunning, garyCommand)
    }.stateIn(scope, SharingStarted.Eagerly, AppUiState())
}
