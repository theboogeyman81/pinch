package com.pinch.gary.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinch.gary.core.theme.GaryAccentIdle
import com.pinch.gary.core.theme.GaryAccentListening
import com.pinch.gary.core.theme.GaryTheme
import com.pinch.gary.ui.components.StatusDot
import com.pinch.gary.vision.PhoneCameraViewModel

/**
 * "A presence, not an app" — status dot + one line of state, no buttons.
 * Per CLAUDE.md's UI spec, this screen intentionally has nothing else on it.
 *
 * Also binds the phone's camera (vision/'s v0 substitute for the glasses
 * camera, per ADR-010) for as long as this screen is on screen — no visible
 * UI change, purely a side effect, and no background capture since it's
 * unbound the moment this leaves composition.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    cameraViewModel: PhoneCameraViewModel = hiltViewModel()
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        cameraViewModel.bind(lifecycleOwner)
        onDispose { cameraViewModel.unbind() }
    }

    MainScreenContent(state)
}

@Composable
private fun MainScreenContent(state: MainScreenState) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusDot(color = if (state.isActive) GaryAccentListening else GaryAccentIdle)
            Text(
                text = state.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            state.batteryPercent?.let { percent ->
                Text(
                    text = "$percent% battery",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    GaryTheme {
        MainScreenContent(MainScreenState(label = "Listening…", isActive = true, batteryPercent = 82))
    }
}
