package com.pinch.gary.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinch.gary.core.appstate.AppState
import com.pinch.gary.glasses.model.GlassesConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainScreenState(
    val label: String,
    val isActive: Boolean,
    val batteryPercent: Int? = null
)

/**
 * Depends only on [AppState] — never on [com.pinch.gary.glasses.GlassesManager]
 * or any other manager directly, per the layering rule in ADR-007.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    appState: AppState
) : ViewModel() {

    val screenState: StateFlow<MainScreenState> = appState.uiState
        .map { it.glassesConnectionState.toMainScreenState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainScreenState("Idle", false))

    private fun GlassesConnectionState.toMainScreenState(): MainScreenState = when (this) {
        is GlassesConnectionState.Disconnected -> MainScreenState("Idle", isActive = false)
        is GlassesConnectionState.Scanning -> MainScreenState("Looking for your glasses…", isActive = false)
        is GlassesConnectionState.Reconnecting -> MainScreenState("Reconnecting…", isActive = false)
        is GlassesConnectionState.BleConnected ->
            MainScreenState("Connected", isActive = true, batteryPercent = device.batteryPercent)
        is GlassesConnectionState.Streaming ->
            MainScreenState("Listening…", isActive = true, batteryPercent = device.batteryPercent)
    }
}
