package com.pinch.gary.vision

import app.cash.turbine.test
import com.pinch.gary.glasses.mjpeg.FrameRingBuffer
import com.pinch.gary.vision.decode.DecodedFrame
import com.pinch.gary.vision.decode.FrameDecoder
import com.pinch.gary.vision.detector.HandLandmarkDetector
import com.pinch.gary.vision.detector.ObjectDetector
import com.pinch.gary.vision.model.Handedness
import com.pinch.gary.vision.model.HandLandmarks
import com.pinch.gary.vision.model.NormalizedLandmark
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionEngineImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val frameRingBuffer = FrameRingBuffer()
    private val frameDecoder = mockk<FrameDecoder>()
    private val handLandmarkDetector = mockk<HandLandmarkDetector>()
    private val objectDetector = mockk<ObjectDetector>()

    private val sampleHand = HandLandmarks(
        points = List(21) { NormalizedLandmark(0f, 0f, 0f) },
        handedness = Handedness.RIGHT
    )

    private fun buildEngine(): VisionEngineImpl = VisionEngineImpl(
        frameRingBuffer = frameRingBuffer,
        frameDecoder = frameDecoder,
        handLandmarkDetector = handLandmarkDetector,
        objectDetector = objectDetector,
        defaultDispatcher = testDispatcher,
        scope = CoroutineScope(SupervisorJob() + testDispatcher)
    )

    @Test
    fun `emits vision result from the first decoded frame`() = runTest(testDispatcher) {
        val fakeFrame = object : DecodedFrame {}
        every { frameDecoder.decode(any()) } returns fakeFrame
        every { handLandmarkDetector.detect(fakeFrame) } returns listOf(sampleHand)
        every { objectDetector.detect(fakeFrame) } returns emptyList()
        frameRingBuffer.push(byteArrayOf(1))

        val engine = buildEngine()
        engine.visionResult.test {
            assertNull(awaitItem())
            engine.start()
            advanceTimeBy(200)
            val result = awaitItem()
            assertEquals(listOf(sampleHand), result?.handLandmarks)
        }
    }

    @Test
    fun `does not reprocess an unchanged frame`() = runTest(testDispatcher) {
        val fakeFrame = object : DecodedFrame {}
        every { frameDecoder.decode(any()) } returns fakeFrame
        every { handLandmarkDetector.detect(fakeFrame) } returns emptyList()
        every { objectDetector.detect(fakeFrame) } returns emptyList()
        frameRingBuffer.push(byteArrayOf(1))

        val engine = buildEngine()
        engine.start()
        advanceTimeBy(500)

        verify(exactly = 1) { frameDecoder.decode(any()) }
    }

    @Test
    fun `decode failure does not emit a result`() = runTest(testDispatcher) {
        every { frameDecoder.decode(any()) } returns null
        frameRingBuffer.push(byteArrayOf(1))

        val engine = buildEngine()
        engine.visionResult.test {
            assertNull(awaitItem())
            engine.start()
            advanceTimeBy(200)
            expectNoEvents()
        }
    }

    @Test
    fun `stop cancels polling and clears result`() = runTest(testDispatcher) {
        val fakeFrame = object : DecodedFrame {}
        every { frameDecoder.decode(any()) } returns fakeFrame
        every { handLandmarkDetector.detect(fakeFrame) } returns listOf(sampleHand)
        every { objectDetector.detect(fakeFrame) } returns emptyList()
        frameRingBuffer.push(byteArrayOf(1))

        val engine = buildEngine()
        engine.start()
        advanceTimeBy(200)
        engine.stop()

        assertNull(engine.visionResult.value)
        assertFalse(engine.isRunning.value)
    }
}
