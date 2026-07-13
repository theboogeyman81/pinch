package com.pinch.gary.gesture.source

import app.cash.turbine.test
import com.pinch.gary.gesture.model.RawGestureEvent
import com.pinch.gary.gesture.model.RawGestureType
import com.pinch.gary.vision.VisionEngine
import com.pinch.gary.vision.model.VisionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Thin adapter test — the heavy heuristic logic is covered by LandmarkGestureClassifierImplTest. */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraGestureSourceImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val visionResultFlow = MutableStateFlow<VisionResult?>(null)
    private val visionEngine = mockk<VisionEngine>()
    private val classifier = mockk<LandmarkGestureClassifier>()

    private fun buildSource(): CameraGestureSourceImpl {
        every { visionEngine.visionResult } returns visionResultFlow
        return CameraGestureSourceImpl(
            visionEngine = visionEngine,
            classifier = classifier,
            scope = CoroutineScope(SupervisorJob() + testDispatcher)
        )
    }

    @Test
    fun `forwards each vision result to the classifier and re-emits its output`() = runTest(testDispatcher) {
        val result = VisionResult(handLandmarks = emptyList(), detectedObjects = emptyList(), frameTimestampMs = 42L)
        val expectedEvent = RawGestureEvent(RawGestureType.PINCH, 42L)
        every { classifier.classify(result.handLandmarks, result.frameTimestampMs) } returns listOf(expectedEvent)

        val source = buildSource()
        source.events.test {
            source.start()
            testDispatcher.scheduler.runCurrent()

            visionResultFlow.value = result
            testDispatcher.scheduler.runCurrent()

            assertEquals(expectedEvent, awaitItem())
        }
    }

    @Test
    fun `null vision result is ignored`() = runTest(testDispatcher) {
        val source = buildSource()
        source.events.test {
            source.start()
            testDispatcher.scheduler.runCurrent()
            expectNoEvents()
        }
    }
}
