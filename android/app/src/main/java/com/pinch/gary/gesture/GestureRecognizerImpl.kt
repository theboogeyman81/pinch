package com.pinch.gary.gesture

import com.pinch.gary.core.di.ApplicationScope
import com.pinch.gary.gesture.model.GaryCommand
import com.pinch.gary.gesture.model.RawGestureEvent
import com.pinch.gary.gesture.model.RawGestureType
import com.pinch.gary.gesture.source.GestureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Suppresses duplicate identical events (e.g. a sustained gesture re-firing
// the same raw type back to back). THUMB_SLIDE is exempt — it legitimately
// repeats every sample while contact holds.
private const val DEBOUNCE_MS = 200L

/**
 * Depends only on GestureSource (ADR-010) — never on GlassesManager or
 * VisionEngine directly, so this class needs zero manager mocks in tests
 * and never changes when a RadarGestureSourceImpl is added later.
 */
@Singleton
class GestureRecognizerImpl @Inject constructor(
    private val gestureSource: GestureSource,
    @ApplicationScope private val scope: CoroutineScope
) : GestureRecognizer {

    private val _commands = MutableSharedFlow<GaryCommand>(extraBufferCapacity = 16)
    override val commands: SharedFlow<GaryCommand> = _commands.asSharedFlow()

    private var collectJob: Job? = null
    private var lastType: RawGestureType? = null
    private var lastAtMs: Long = 0L

    override fun start() {
        if (collectJob?.isActive == true) return
        gestureSource.start()
        collectJob = scope.launch {
            gestureSource.events.collect { event -> onEvent(event) }
        }
    }

    private fun onEvent(event: RawGestureEvent) {
        val isDuplicate = event.type == lastType &&
            event.type != RawGestureType.THUMB_SLIDE &&
            (event.timestampMs - lastAtMs) < DEBOUNCE_MS
        lastType = event.type
        lastAtMs = event.timestampMs
        if (isDuplicate) return
        mapToCommand(event)?.let { _commands.tryEmit(it) }
    }

    override fun stop() {
        collectJob?.cancel()
        collectJob = null
        gestureSource.stop()
        lastType = null
        lastAtMs = 0L
    }

    private fun mapToCommand(event: RawGestureEvent): GaryCommand = when (event.type) {
        RawGestureType.PINCH -> GaryCommand.Pinch
        RawGestureType.DOUBLE_PINCH -> GaryCommand.DoublePinch
        RawGestureType.HOLD_PINCH -> GaryCommand.HoldPinch
        RawGestureType.SWIPE_RIGHT -> GaryCommand.SwipeRight
        RawGestureType.SWIPE_LEFT -> GaryCommand.SwipeLeft
        RawGestureType.SWIPE_UP -> GaryCommand.SwipeUp
        RawGestureType.SWIPE_DOWN -> GaryCommand.SwipeDown
        RawGestureType.AIR_TAP -> GaryCommand.AirTap
        RawGestureType.GRAB -> GaryCommand.Grab
        RawGestureType.THUMB_SLIDE -> GaryCommand.ThumbSlide(event.magnitude ?: 0f)
    }
}
