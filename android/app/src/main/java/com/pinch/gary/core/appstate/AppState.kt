package com.pinch.gary.core.appstate

import com.pinch.gary.core.di.ApplicationScope
import com.pinch.gary.glasses.GlassesManager
import com.pinch.gary.glasses.model.GlassesConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for screen-level UI state — see ADR-007. */
data class AppUiState(
    val glassesConnectionState: GlassesConnectionState = GlassesConnectionState.Disconnected
)

/**
 * Combines every feature manager's [StateFlow] into one [AppUiState]. Only
 * wires in [GlassesManager] for now; add a manager to the `combine()` here
 * the same week its package is scaffolded (gesture/voice/etc.), never before.
 *
 * Screen ViewModels depend on this class only, never on individual managers.
 */
@Singleton
class AppState @Inject constructor(
    glassesManager: GlassesManager,
    @ApplicationScope scope: CoroutineScope
) {
    val uiState: StateFlow<AppUiState> = glassesManager.connectionState
        .map { AppUiState(glassesConnectionState = it) }
        .stateIn(scope, SharingStarted.Eagerly, AppUiState())
}
