package com.pinch.gary.gesture

import app.cash.turbine.test
import com.pinch.gary.gesture.model.GaryCommand
import com.pinch.gary.gesture.model.RawGestureEvent
import com.pinch.gary.gesture.model.RawGestureType
import com.pinch.gary.gesture.source.GestureSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * No GlassesManager/VisionEngine mocks needed here — per ADR-010,
 * GestureRecognizerImpl depends only on GestureSource.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GestureRecognizerImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val sourceEvents = MutableSharedFlow<RawGestureEvent>(extraBufferCapacity = 16)
    private val gestureSource = mockk<GestureSource>(relaxed = true)

    private fun buildRecognizer(): GestureRecognizerImpl {
        every { gestureSource.events } returns sourceEvents
        return GestureRecognizerImpl(
            gestureSource = gestureSource,
            scope = CoroutineScope(SupervisorJob() + testDispatcher)
        )
    }

    @Test
    fun `PINCH event emits Pinch command`() = runTest(testDispatcher) {
        val recognizer = buildRecognizer()
        recognizer.commands.test {
            recognizer.start()
            testDispatcher.scheduler.runCurrent()

            sourceEvents.emit(RawGestureEvent(RawGestureType.PINCH, timestampMs = 0L))
            testDispatcher.scheduler.runCurrent()

            assertEquals(GaryCommand.Pinch, awaitItem())
        }
    }

    @Test
    fun `duplicate PINCH within debounce window is suppressed`() = runTest(testDispatcher) {
        val recognizer = buildRecognizer()
        recognizer.commands.test {
            recognizer.start()
            testDispatcher.scheduler.runCurrent()

            sourceEvents.emit(RawGestureEvent(RawGestureType.PINCH, timestampMs = 0L))
            testDispatcher.scheduler.runCurrent()
            assertEquals(GaryCommand.Pinch, awaitItem())

            sourceEvents.emit(RawGestureEvent(RawGestureType.PINCH, timestampMs = 50L))
            testDispatcher.scheduler.runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `PINCH after debounce window elapses fires again`() = runTest(testDispatcher) {
        val recognizer = buildRecognizer()
        recognizer.commands.test {
            recognizer.start()
            testDispatcher.scheduler.runCurrent()

            sourceEvents.emit(RawGestureEvent(RawGestureType.PINCH, timestampMs = 0L))
            testDispatcher.scheduler.runCurrent()
            assertEquals(GaryCommand.Pinch, awaitItem())

            sourceEvents.emit(RawGestureEvent(RawGestureType.PINCH, timestampMs = 250L))
            testDispatcher.scheduler.runCurrent()
            assertEquals(GaryCommand.Pinch, awaitItem())
        }
    }

    @Test
    fun `THUMB_SLIDE repeats are never debounced`() = runTest(testDispatcher) {
        val recognizer = buildRecognizer()
        recognizer.commands.test {
            recognizer.start()
            testDispatcher.scheduler.runCurrent()

            sourceEvents.emit(RawGestureEvent(RawGestureType.THUMB_SLIDE, timestampMs = 0L, magnitude = 0.1f))
            testDispatcher.scheduler.runCurrent()
            assertEquals(GaryCommand.ThumbSlide(0.1f), awaitItem())

            sourceEvents.emit(RawGestureEvent(RawGestureType.THUMB_SLIDE, timestampMs = 10L, magnitude = 0.2f))
            testDispatcher.scheduler.runCurrent()
            assertEquals(GaryCommand.ThumbSlide(0.2f), awaitItem())
        }
    }
}
