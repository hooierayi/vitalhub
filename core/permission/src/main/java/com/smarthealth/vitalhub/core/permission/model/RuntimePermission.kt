package com.smarthealth.vitalhub.core.permission.model

import android.os.Build

/** Dynamically injected definition of one runtime-permission capability. */
class RuntimePermission(
    val id: String,
    val description: String,
    val requestCode: Int,
    private val permissionsProvider: (sdkInt: Int) -> List<String>,
    /** Request again after a previous denial instead of showing the in-app fallback immediately. */
    val isCompulsive: Boolean = false,
    /** Whether callers should re-check when returning from the app settings page. */
    val checkOnPageStarted: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Permission id must not be blank." }
        require(description.isNotBlank()) { "Permission description must not be blank." }
        require(requestCode > 0) { "Permission requestCode must be positive." }
    }

    fun permissionsForSdk(sdkInt: Int): List<String> = permissionsProvider(sdkInt).also {
        require(it.isNotEmpty()) { "Permission $id must provide at least one Android permission." }
    }

    internal fun currentPermissions(): List<String> = permissionsForSdk(Build.VERSION.SDK_INT)
}
