package com.pinch.gary.gesture

import com.pinch.gary.gesture.model.GaryCommand
import kotlinx.coroutines.flow.SharedFlow

interface GestureRecognizer {
    val commands: SharedFlow<GaryCommand>

    fun start()
    fun stop()
}
