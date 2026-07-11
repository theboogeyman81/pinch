package com.pinch.gary.core.permissions

/** Aggregate grant state for a permission group (e.g. [RequiredPermissions.forGlasses]). */
data class PermissionState(
    val granted: Set<String>,
    val denied: Set<String>
) {
    fun isFullyGranted(required: Array<String>): Boolean =
        required.all { it in granted }

    companion object {
        fun from(required: Array<String>, isGranted: (String) -> Boolean): PermissionState {
            val granted = required.filter(isGranted).toSet()
            val denied = required.filterNot(isGranted).toSet()
            return PermissionState(granted, denied)
        }
    }
}
