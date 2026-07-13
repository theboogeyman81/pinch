package com.pinch.gary.vision.detector

import android.content.Context
import com.pinch.gary.core.util.Logger
import com.pinch.gary.vision.decode.DecodedFrame
import com.pinch.gary.vision.model.HandLandmarks
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaPipeHandLandmarkDetector"

/**
 * Real class, real constructor — so the Hilt DI graph is complete today —
 * but [detect] is a TODO stub. Same placeholder convention as
 * BleGattProfile's UUIDs: this needs `hand_landmarker.task` in
 * assets/models/, which does not exist in this repo yet. Once it lands,
 * construct a `com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker`
 * (RunningMode.IMAGE) here and replace this stub.
 */
@Singleton
class MediaPipeHandLandmarkDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : HandLandmarkDetector {

    override fun detect(frame: DecodedFrame): List<HandLandmarks> {
        Logger.d(TAG, "hand_landmarker.task model asset not present — returning no hands")
        return emptyList()
    }
}
