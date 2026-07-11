package com.pinch.gary.glasses

import com.pinch.gary.glasses.model.GlassesConnectionState
import com.pinch.gary.glasses.model.GlassesEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface boundary around the BLE/MJPEG hardware surface. Per ADR-008
 * (.claude/save/architecture-decisions.md), this exists so everything above
 * it — ViewModels, AppState, tests — can depend on a fake instead of real
 * Bluetooth/WiFi, since the emulator can't exercise either reliably.
 */
interface GlassesManager {
    val connectionState: StateFlow<GlassesConnectionState>
    val events: SharedFlow<GlassesEvent>

    fun startScanning()
    fun disconnect()
}
