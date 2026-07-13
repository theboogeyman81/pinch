package com.pinch.gary.vision

import com.pinch.gary.core.di.ApplicationScope
import com.pinch.gary.core.di.DefaultDispatcher
import com.pinch.gary.glasses.mjpeg.FrameRingBuffer
import com.pinch.gary.vision.decode.FrameDecoder
import com.pinch.gary.vision.detector.HandLandmarkDetector
import com.pinch.gary.vision.detector.ObjectDetector
import com.pinch.gary.vision.model.VisionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val POLL_INTERVAL_MS = 66L // ~15fps — vision is fallback-only per CLAUDE.md, radar is primary

/**
 * Polls [FrameRingBuffer] (no Flow API by design) on a background coroutine,
 * decodes + runs inference, and emits a [VisionResult]. The producer feeding
 * FrameRingBuffer is source-agnostic — today it's the phone's own camera
 * (vision/camera/CameraXPhoneCameraSource, per ADR-010), later it'll be the
 * ESP32 MJPEG stream. This class doesn't know or care which.
 */
@Singleton
class VisionEngineImpl @Inject constructor(
    private val frameRingBuffer: FrameRingBuffer,
    private val frameDecoder: FrameDecoder,
    private val handLandmarkDetector: HandLandmarkDetector,
    private val objectDetector: ObjectDetector,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
) : VisionEngine {

    private val _visionResult = MutableStateFlow<VisionResult?>(null)
    override val visionResult: StateFlow<VisionResult?> = _visionResult.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var pollingJob: Job? = null
    private var lastProcessedFrame: ByteArray? = null

    override fun start() {
        if (pollingJob?.isActive == true) return
        _isRunning.value = true
        pollingJob = scope.launch {
            while (isActive) {
                frameRingBuffer.latest()
                    ?.takeIf { it !== lastProcessedFrame }
                    ?.let { frame ->
                        lastProcessedFrame = frame
                        processFrame(frame)
                    }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun processFrame(jpegBytes: ByteArray) = withContext(defaultDispatcher) {
        val decoded = frameDecoder.decode(jpegBytes) ?: return@withContext
        val hands = handLandmarkDetector.detect(decoded)
        val objects = objectDetector.detect(decoded)
        _visionResult.value = VisionResult(hands, objects, System.currentTimeMillis())
    }

    override fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        _isRunning.value = false
        _visionResult.value = null
        lastProcessedFrame = null
    }
}
