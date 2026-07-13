package com.pinch.gary.vision.decode

interface FrameDecoder {
    /** Returns null on a corrupt/truncated JPEG — never throws. */
    fun decode(jpegBytes: ByteArray): DecodedFrame?
}
