package com.pinch.gary.ui.permissions

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pinch.gary.core.permissions.RequiredPermissions

/**
 * Requests what glasses/ needs (BLE + notifications), what vision/ needs
 * (camera — v0 phone-camera substitute per ADR-010), and location. Location
 * is requested here too since SmartHomeManager (week 9-10) will need it, but
 * its "am I home?" logic isn't wired up yet — granting it early avoids a
 * second interruption later.
 */
@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val permissionsToRequest = remember {
        (RequiredPermissions.forGlasses + RequiredPermissions.location + RequiredPermissions.forVision)
            .distinct()
            .toTypedArray()
    }

    var allGranted by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allGranted = results.values.all { it }
        if (allGranted) onAllGranted()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Gary needs Bluetooth, location, and camera access to find your glasses " +
                    "and recognize gestures.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    if (allGranted) onAllGranted() else launcher.launch(permissionsToRequest)
                }
            ) {
                Text(if (allGranted) "Continue" else "Grant permissions")
            }
        }
    }
}
