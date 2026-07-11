package com.pinch.gary.core.permissions

import android.Manifest
import android.os.Build

/**
 * Runtime permission groups, grouped by which feature requests them.
 * Declaring these in the manifest (done) is separate from requesting them
 * at runtime (done per-feature, on the week that feature is built) — this
 * object is what `ui/permissions` reads from.
 */
object RequiredPermissions {

    val ble: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    val microphone: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    val location: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val backgroundLocation: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }

    val notifications: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    /** Permissions the glasses/ feature (week 1-2) needs to function at all. */
    val forGlasses: Array<String> = ble + notifications
}
