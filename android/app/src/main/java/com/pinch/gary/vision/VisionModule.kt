package com.pinch.gary.vision

import com.pinch.gary.vision.camera.CameraXPhoneCameraSource
import com.pinch.gary.vision.camera.PhoneCameraSource
import com.pinch.gary.vision.decode.BitmapFrameDecoder
import com.pinch.gary.vision.decode.FrameDecoder
import com.pinch.gary.vision.detector.HandLandmarkDetector
import com.pinch.gary.vision.detector.MediaPipeHandLandmarkDetector
import com.pinch.gary.vision.detector.ObjectDetector
import com.pinch.gary.vision.detector.YoloObjectDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisionModule {

    @Binds
    @Singleton
    abstract fun bindsVisionEngine(impl: VisionEngineImpl): VisionEngine

    @Binds
    @Singleton
    abstract fun bindsFrameDecoder(impl: BitmapFrameDecoder): FrameDecoder

    @Binds
    @Singleton
    abstract fun bindsHandLandmarkDetector(impl: MediaPipeHandLandmarkDetector): HandLandmarkDetector

    @Binds
    @Singleton
    abstract fun bindsObjectDetector(impl: YoloObjectDetector): ObjectDetector

    @Binds
    @Singleton
    abstract fun bindsPhoneCameraSource(impl: CameraXPhoneCameraSource): PhoneCameraSource
}
