package com.pinch.gary.vision.detector

import com.pinch.gary.vision.decode.DecodedFrame
import com.pinch.gary.vision.model.DetectedObject

interface ObjectDetector {
    fun detect(frame: DecodedFrame): List<DetectedObject>
}
