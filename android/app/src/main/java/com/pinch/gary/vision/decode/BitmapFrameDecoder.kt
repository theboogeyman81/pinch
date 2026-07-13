package com.pinch.gary.vision.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Real [DecodedFrame] — the only handle type detectors actually receive at runtime. */
class BitmapDecodedFrame(val bitmap: Bitmap) : DecodedFrame

/**
 * The only class in vision/ that touches android.graphics.* directly. Not
 * unit tested (would need Robolectric) — flagged as a manual-verification
 * gap in .claude/save/features/vision-engine.md.
 */
@Singleton
class BitmapFrameDecoder @Inject constructor() : FrameDecoder {
    override fun decode(jpegBytes: ByteArray): DecodedFrame? {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        return BitmapDecodedFrame(bitmap)
    }
}
