package com.pinch.gary.vision.detector

import android.content.Context
import com.pinch.gary.core.util.Logger
import com.pinch.gary.vision.decode.DecodedFrame
import com.pinch.gary.vision.model.DetectedObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YoloObjectDetector"

/**
 * Same placeholder shape as [MediaPipeHandLandmarkDetector] — [detect] is a
 * TODO stub pending `yolov8n.tflite` in assets/models/, which does not exist
 * in this repo yet. Once it lands, load it via
 * `org.tensorflow.lite.support` here and replace this stub.
 */
@Singleton
class YoloObjectDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : ObjectDetector {

    override fun detect(frame: DecodedFrame): List<DetectedObject> {
        Logger.d(TAG, "yolov8n.tflite model asset not present — returning no objects")
        return emptyList()
    }
}
