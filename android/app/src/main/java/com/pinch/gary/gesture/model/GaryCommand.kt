package com.pinch.gary.gesture.model

/** CLAUDE.md's full gesture vocabulary, translated from raw sensor signal to intent. */
sealed interface GaryCommand {
    data object Pinch : GaryCommand // wake / confirm
    data object DoublePinch : GaryCommand // mode switch
    data object HoldPinch : GaryCommand // push-to-talk trigger
    data object SwipeRight : GaryCommand // next / skip / dismiss
    data object SwipeLeft : GaryCommand // back
    data object SwipeUp : GaryCommand // volume/brightness up — semantics decided by whichever mode is active
    data object SwipeDown : GaryCommand // volume/brightness down
    data object AirTap : GaryCommand // single select
    data object Grab : GaryCommand // stop / cancel
    data class ThumbSlide(val delta: Float) : GaryCommand // continuous brightness slider, signed delta
}
