package com.pinch.gary.glasses.mjpeg

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fixed-size, RAM-only ring buffer of decoded JPEG frames (~50-90KB each on
 * a 640x480 stream, so 3-5 frames stays well under the ~250KB budget in
 * CLAUDE.md's privacy rules). No frame is ever written to disk here or
 * anywhere downstream — this class has no `write()`/file APIs at all, by
 * design, not by convention.
 *
 * The future VisionEngine (week 3-4) reads [latest] on its own background
 * coroutine; until then frames are simply decoded and dropped once evicted.
 */
@Singleton
class FrameRingBuffer @Inject constructor() {

    private val maxSize = 5
    private val buffer = ArrayDeque<ByteArray>(maxSize)

    @Synchronized
    fun push(frame: ByteArray) {
        if (buffer.size >= maxSize) {
            buffer.removeFirst()
        }
        buffer.addLast(frame)
    }

    @Synchronized
    fun latest(): ByteArray? = buffer.peekLast()

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
