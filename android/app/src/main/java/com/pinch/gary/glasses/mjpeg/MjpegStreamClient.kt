package com.pinch.gary.glasses.mjpeg

import com.pinch.gary.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MjpegStreamClient"
private const val JPEG_SOI = 0xFFD8 // Start Of Image marker
private const val JPEG_EOI = 0xFFD9 // End Of Image marker
private const val MAX_FRAME_BYTES = 2_000_000 // guards against a malformed stream growing unbounded

/**
 * Parses a `multipart/x-mixed-replace` MJPEG HTTP stream from the ESP32
 * glasses into a [Flow] of individual JPEG frame byte arrays. Frames are
 * found by scanning for JPEG SOI/EOI markers directly rather than parsing
 * multipart boundary headers — simpler and firmware-agnostic as long as the
 * ESP32 emits standard JPEG frames back-to-back.
 *
 * Emitted frames are only ever handed to [FrameRingBuffer]; this class does
 * not persist anything itself.
 */
@Singleton
class MjpegStreamClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    /** [streamUrl] is the ESP32's MJPEG endpoint, discovered/configured during BLE pairing — never hardcoded. */
    fun stream(streamUrl: String): Flow<ByteArray> = callbackFlow {
        val request = Request.Builder().url(streamUrl).build()
        var call: Call? = null

        try {
            call = okHttpClient.newCall(request)
            val response = call.execute()
            val body = response.body ?: run {
                close(IllegalStateException("MJPEG response had no body"))
                return@callbackFlow
            }

            val input = BufferedInputStream(body.byteStream())
            Logger.d(TAG, "MJPEG stream opened")

            readFrames(input) { frame -> trySend(frame) }
        } catch (t: Throwable) {
            Logger.w(TAG, "MJPEG stream terminated", t)
            close(t)
        }

        awaitClose {
            call?.cancel()
            Logger.d(TAG, "MJPEG stream closed")
        }
    }.flowOn(Dispatchers.IO)

    private fun readFrames(input: InputStream, onFrame: (ByteArray) -> Unit) {
        val buffer = java.io.ByteArrayOutputStream()
        var inFrame = false
        var prevByte = -1

        while (true) {
            val current = input.read()
            if (current == -1) break // stream ended (disconnect)

            if (!inFrame) {
                if (prevByte == 0xFF && current == 0xD8) {
                    inFrame = true
                    buffer.reset()
                    buffer.write(0xFF)
                    buffer.write(0xD8)
                }
            } else {
                buffer.write(current)
                if (prevByte == 0xFF && current == 0xD9) {
                    inFrame = false
                    onFrame(buffer.toByteArray())
                    buffer.reset()
                } else if (buffer.size() > MAX_FRAME_BYTES) {
                    Logger.w(TAG, "Discarding oversized/malformed frame")
                    inFrame = false
                    buffer.reset()
                }
            }
            prevByte = current
        }
    }
}
