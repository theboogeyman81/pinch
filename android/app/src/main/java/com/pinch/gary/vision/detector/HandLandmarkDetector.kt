package com.pinch.gary.vision.detector

import com.pinch.gary.vision.decode.DecodedFrame
import com.pinch.gary.vision.model.HandLandmarks

interface HandLandmarkDetector {
    fun detect(frame: DecodedFrame): List<HandLandmarks>
}
