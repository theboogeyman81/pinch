package com.pinch.gary.glasses.mjpeg

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRingBufferTest {

    @Test
    fun `latest returns null when empty`() {
        val buffer = FrameRingBuffer()
        assertNull(buffer.latest())
    }

    @Test
    fun `latest returns most recently pushed frame`() {
        val buffer = FrameRingBuffer()
        buffer.push(byteArrayOf(1))
        buffer.push(byteArrayOf(2))

        assertArrayEquals(byteArrayOf(2), buffer.latest())
    }

    @Test
    fun `evicts oldest frame once max size is exceeded`() {
        val buffer = FrameRingBuffer()
        repeat(6) { i -> buffer.push(byteArrayOf(i.toByte())) }

        // capacity is 5; frame 0 should have been evicted, frame 5 should be latest
        assertArrayEquals(byteArrayOf(5), buffer.latest())
    }

    @Test
    fun `clear empties the buffer`() {
        val buffer = FrameRingBuffer()
        buffer.push(byteArrayOf(1))
        buffer.clear()

        assertNull(buffer.latest())
    }
}
