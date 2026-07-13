package com.pinch.gary.vision.decode

/**
 * Opaque handle to a decoded frame. The real implementation wraps
 * [android.graphics.Bitmap]; unit tests use a plain `object : DecodedFrame {}`
 * so [com.pinch.gary.vision.VisionEngineImpl]'s orchestration logic never
 * needs a real Bitmap or Android runtime to be tested.
 */
interface DecodedFrame
