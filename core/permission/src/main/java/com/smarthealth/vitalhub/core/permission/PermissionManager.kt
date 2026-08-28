package com.smarthealth.vitalhub.core.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission
import java.util.concurrent.ConcurrentHashMap

/**
 * Configurable permission service. Its shape intentionally follows the reference PermissionManager,
 * while the app injects all permission definitions and host UI dependencies.
 */
object PermissionManager : PermissionServiceProvider {
    private const val DENIED_FLAG_PREFIX = "permission_denied:"
    private val permissions = ConcurrentHashMap<String, RuntimePermission>()
    private val routePermissions = ConcurrentHashMap<String, String>()
    private val handler = PermissionHandler()

    @Volatile
    private var config: PermissionServiceInitConfig? = null

    override fun init(config: PermissionServiceInitConfig) {
        this.config = config
        permissions.clear()
        routePermissions.clear()
        config.permissions.forEach(::register)
        config.routePermissions.forEach(::bindRoute)
    }

    override fun register(permission: RuntimePermission) {
        permissions[permission.id] = permission
    }

    override fun unregister(permissionId: String) {
        permissions.remove(permissionId)
        routePermissions.entries.removeAll { it.value == permissionId }
    }

    override fun bindRoute(routePath: String, permissionId: String) {
        require(routePath.startsWith('/')) { "Route path must start with '/'." }
        require(permissions.containsKey(permissionId)) { "Unknown permission id: $permissionId" }
        routePermissions[routePath] = permissionId
    }

    override fun unbindRoute(routePath: String) {
        routePermissions.remove(routePath)
    }

    override fun permissionForRoute(routePath: String): RuntimePermission? =
        routePermissions[routePath]?.let(permissions::get)

    override fun guardedRoutes(): Set<String> = routePermissions.keys.toSet()

    override fun requestPermission(permission: RuntimePermission, callback: PermissionServiceProvider.RequestCallback?): Int {
        return if (Looper.myLooper() == Looper.getMainLooper()) handler.request(permission, callback)
        else {
            val activity = topActivity()
            if (activity == null) {
                callback?.onPermissionDenied(permission)
            } else {
                activity.runOnUiThread { handler.request(permission, callback) }
            }
            PermissionServiceProvider.REQUEST_DENIED
        }
    }

    override fun checkPermissionAsync(
        permission: RuntimePermission,
        needPreHandle: Boolean,
        callback: PermissionServiceProvider.CheckCallback?,
    ) = handler.checkAsync(permission, needPreHandle, callback)

    override fun checkPermissionSync(permission: RuntimePermission, needPreHandle: Boolean): Boolean =
        handler.checkSync(permission, needPreHandle)

    override fun gotoAppSettingPage(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    internal fun appContext(): Context = requireNotNull(config) { "PermissionManager.init must be called first." }.appContext

    internal fun topActivity(): FragmentActivity? = config?.topActivityProvider?.invoke()

    internal fun storePermissionDeniedFlag(permission: RuntimePermission) {
        requireNotNull(config) { "PermissionManager.init must be called first." }
            .storage.putBoolean(DENIED_FLAG_PREFIX + permission.id, true)
    }

    internal fun isPermissionHasDenied(permission: RuntimePermission): Boolean =
        config?.storage?.getBoolean(DENIED_FLAG_PREFIX + permission.id, false) ?: false

    internal fun showPermissionDeniedDialog(activity: FragmentActivity, permission: RuntimePermission) {
        val currentConfig = config ?: return
        currentConfig.dialogService.showPermissionDenied(
            activity = activity,
            permission = permission,
            onCancel = {},
            onOpenSettings = { gotoAppSettingPage(activity) },
        )
    }

    private class PermissionHandler {
        fun request(permission: RuntimePermission, callback: PermissionServiceProvider.RequestCallback?): Int {
            val activity = topActivity() ?: run {
                callback?.onPermissionDenied(permission)
                return PermissionServiceProvider.REQUEST_DENIED
            }
            if (SystemPermissionChecker.checkSelfPermission(activity, permission)) {
                callback?.onPermissionGranted(permission)
                return PermissionServiceProvider.REQUEST_GRANTED
            }
            if (SystemPermissionChecker.shouldShowRequestPermissionRationale(activity, permission) ||
                (!permission.isCompulsive && isPermissionHasDenied(permission))
            ) {
                handlePermissionDenied(activity, permission, callback)
                return PermissionServiceProvider.REQUEST_DENIED
            }
            return try {
                SystemPermissionChecker.request(activity, permission, object : PermissionServiceProvider.RequestCallback {
                    override fun onPermissionGranted(permission: RuntimePermission) {
                        callback?.onPermissionGranted(permission)
                    }

                    override fun onPermissionDenied(permission: RuntimePermission) {
                        handlePermissionDenied(activity, permission, callback)
                    }
                })
                PermissionServiceProvider.REQUEST_DENIED
            } catch (_: IllegalStateException) {
                handlePermissionDenied(activity, permission, callback)
                PermissionServiceProvider.REQUEST_DENIED
            }
        }

        fun checkAsync(
            permission: RuntimePermission,
            needPreHandle: Boolean,
            callback: PermissionServiceProvider.CheckCallback?,
        ) {
            val granted = SystemPermissionChecker.checkSelfPermission(appContext(), permission)
            if (granted) callback?.onPermissionGranted(permission)
            else if (needPreHandle) handlePermissionDenied(topActivity(), permission, callback)
            else callback?.onPermissionDenied(permission)
        }

        fun checkSync(permission: RuntimePermission, needPreHandle: Boolean): Boolean {
            val granted = SystemPermissionChecker.checkSelfPermission(appContext(), permission)
            if (!granted && needPreHandle) handlePermissionDenied(topActivity(), permission, null)
            return granted
        }

        private fun handlePermissionDenied(
            activity: FragmentActivity?,
            permission: RuntimePermission,
            callback: PermissionServiceProvider.RequestCallback?,
        ) {
            storePermissionDeniedFlag(permission)
            callback?.onPermissionDenied(permission)
            activity?.let { showPermissionDeniedDialog(it, permission) }
        }
    }
}
