package com.pinch.gary.glasses.model

/** State machine per CLAUDE.md: Disconnected -> Scanning -> BleConnected -> Streaming. */
sealed interface GlassesConnectionState {
    data object Disconnected : GlassesConnectionState
    data object Scanning : GlassesConnectionState
    data class BleConnected(val device: GlassesDevice) : GlassesConnectionState
    data class Streaming(val device: GlassesDevice) : GlassesConnectionState

    /** Reconnect attempt in progress after an unexpected drop, per manager's exponential backoff. */
    data class Reconnecting(val attempt: Int) : GlassesConnectionState
}
