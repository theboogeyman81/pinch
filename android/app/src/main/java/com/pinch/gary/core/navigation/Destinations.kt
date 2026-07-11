package com.pinch.gary.core.navigation

sealed class Destination(val route: String) {
    data object Main : Destination("main")
    data object Onboarding : Destination("onboarding")
    data object Settings : Destination("settings")
    data object Permissions : Destination("permissions")
}
