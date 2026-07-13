package com.pinch.gary.gesture

import com.pinch.gary.gesture.source.CameraGestureSourceImpl
import com.pinch.gary.gesture.source.GestureSource
import com.pinch.gary.gesture.source.LandmarkGestureClassifier
import com.pinch.gary.gesture.source.LandmarkGestureClassifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GestureModule {

    @Binds
    @Singleton
    abstract fun bindsGestureRecognizer(impl: GestureRecognizerImpl): GestureRecognizer

    /**
     * The one line that changes when radar hardware lands (ADR-010): swap
     * CameraGestureSourceImpl for a RadarGestureSourceImpl here.
     */
    @Binds
    @Singleton
    abstract fun bindsGestureSource(impl: CameraGestureSourceImpl): GestureSource

    @Binds
    @Singleton
    abstract fun bindsLandmarkGestureClassifier(impl: LandmarkGestureClassifierImpl): LandmarkGestureClassifier
}
