package com.pinch.gary

import android.app.Application
import com.pinch.gary.core.appstate.GaryOrchestrator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GaryApplication : Application() {

    @Inject
    lateinit var garyOrchestrator: GaryOrchestrator

    override fun onCreate() {
        super.onCreate()
        garyOrchestrator.start()
    }
}
