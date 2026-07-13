package com.pinch.gary.vision.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pinch.gary.core.util.Logger
import com.pinch.gary.glasses.mjpeg.FrameRingBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CameraXPhoneCameraSource"
private const val JPEG_QUALITY = 80

/**
 * Real CameraX capture — the only class in vision/camera/ touching
 * android.graphics / CameraX APIs, not unit tested (manual-verification
 * only, same boundary as BitmapFrameDecoder). Uses ImageAnalysis with
 * OUTPUT_IMAGE_FORMAT_RGBA_8888 (CameraX 1.3+) + ImageProxy.toBitmap()
 * rather than hand-rolling YUV_420_888 conversion — simpler, and per-frame
 * cost is irrelevant here since this is a dev/test substitute for the
 * glasses camera, not shipping firmware. Frames are re-encoded to JPEG so
 * FrameRingBuffer's contract (JPEG ByteArray) stays identical regardless of
 * producer — VisionEngineImpl/FrameDecoder need zero changes.
 *
 * Everything here is RAM-only: the ByteArrayOutputStream used for JPEG
 * encoding is never written to disk, per CLAUDE.md's privacy rules.
 */
@Singleton
class CameraXPhoneCameraSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameRingBuffer: FrameRingBuffer
) : PhoneCameraSource {

    private var cameraProvider: ProcessCameraProvider? = null

    override fun bind(lifecycleOwner: LifecycleOwner) {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            onFrame(imageProxy)
        }

        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        cameraProvider = provider
    }

    private fun onFrame(imageProxy: ImageProxy) {
        try {
            imageProxy.toBitmap().toJpegBytes()?.let(frameRingBuffer::push)
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to convert camera frame to JPEG", t)
        } finally {
            imageProxy.close()
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray? {
        val stream = ByteArrayOutputStream()
        val compressed = compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return if (compressed) stream.toByteArray() else null
    }

    override fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        frameRingBuffer.clear()
    }
}
