package com.pinch.gary.vision

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.pinch.gary.vision.camera.PhoneCameraSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin bind/unbind wrapper so ui/main can drive [PhoneCameraSource] from a
 * Composable's LifecycleOwner — CameraX's bindToLifecycle() needs one, and
 * GaryOrchestrator (Application-scoped, no LifecycleOwner) can't provide it.
 * Mirrors glasses/GlassesViewModel.kt's shape: a small feature ViewModel
 * called directly by UI, not routed through AppState.
 */
@HiltViewModel
class PhoneCameraViewModel @Inject constructor(
    private val phoneCameraSource: PhoneCameraSource
) : ViewModel() {

    fun bind(lifecycleOwner: LifecycleOwner) = phoneCameraSource.bind(lifecycleOwner)

    fun unbind() = phoneCameraSource.unbind()
}
