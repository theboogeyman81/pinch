package com.pinch.gary.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pinch.gary.ui.main.MainScreen
import com.pinch.gary.ui.onboarding.OnboardingScreen
import com.pinch.gary.ui.permissions.PermissionsScreen
import com.pinch.gary.ui.settings.SettingsScreen

/**
 * Single Activity, single NavHost. Starts at Permissions this week (week
 * 1-2, glasses/ only) since Onboarding's real content — glasses pair ->
 * smart home setup -> permissions — doesn't exist until week 11-12.
 */
@Composable
fun GaryNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Permissions.route) {
        composable(Destination.Permissions.route) {
            PermissionsScreen(
                onAllGranted = {
                    navController.navigate(Destination.Main.route) {
                        popUpTo(Destination.Permissions.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Destination.Main.route) { MainScreen() }
        composable(Destination.Onboarding.route) { OnboardingScreen() }
        composable(Destination.Settings.route) { SettingsScreen() }
    }
}
