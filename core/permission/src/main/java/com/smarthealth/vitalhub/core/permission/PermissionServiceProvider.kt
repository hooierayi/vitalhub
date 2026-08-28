package com.smarthealth.vitalhub.core.permission

import android.content.Context
import android.content.pm.PackageManager
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission

/** Public permission-service contract, modelled after the reference core-services API. */
interface PermissionServiceProvider {
    fun init(config: PermissionServiceInitConfig)
    fun register(permission: RuntimePermission)
    fun unregister(permissionId: String)
    fun bindRoute(routePath: String, permissionId: String)
    fun unbindRoute(routePath: String)
    fun permissionForRoute(routePath: String): RuntimePermission?
    fun guardedRoutes(): Set<String>

    fun requestPermission(permission: RuntimePermission, callback: RequestCallback? = null): Int
    fun checkPermissionAsync(
        permission: RuntimePermission,
        needPreHandle: Boolean = false,
        callback: CheckCallback? = null,
    )
    fun checkPermissionSync(permission: RuntimePermission, needPreHandle: Boolean = false): Boolean
    fun gotoAppSettingPage(context: Context): Boolean

    interface RequestCallback {
        fun onPermissionGranted(permission: RuntimePermission)
        fun onPermissionDenied(permission: RuntimePermission)
    }

    interface CheckCallback : RequestCallback

    companion object {
        const val REQUEST_GRANTED = PackageManager.PERMISSION_GRANTED
        const val REQUEST_DENIED = PackageManager.PERMISSION_DENIED
    }
}
