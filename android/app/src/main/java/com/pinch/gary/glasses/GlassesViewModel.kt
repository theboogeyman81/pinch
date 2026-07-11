package com.pinch.gary.glasses

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.pinch.gary.glasses.model.GlassesConnectionState
import com.pinch.gary.glasses.service.GlassesForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Starts/stops [GlassesForegroundService] once BLE permissions are granted.
 * `ui/permissions` is the only current caller — `ui/main` reads glasses
 * connection status through `core/appstate/AppState`, not this ViewModel
 * directly, per the "ViewModels depend only on AppState" layering rule.
 */
@HiltViewModel
class GlassesViewModel @Inject constructor(
    application: Application,
    glassesManager: GlassesManager
) : AndroidViewModel(application) {

    val connectionState: StateFlow<GlassesConnectionState> = glassesManager.connectionState

    fun startGlassesService() {
        val context = getApplication<Application>()
        val intent = Intent(context, GlassesForegroundService::class.java)
        context.startForegroundService(intent)
    }

    fun stopGlassesService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, GlassesForegroundService::class.java))
    }
}
