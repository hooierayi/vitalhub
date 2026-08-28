package com.smarthealth.vitalhub.core.permission

import android.app.AlertDialog
import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission

/**
 * Injectable host dependencies for the permission service.
 *
 * The module owns request mechanics only. Permission definitions, guarded routes and visual
 * treatment are supplied by the application that embeds it.
 */
class PermissionServiceInitConfig(
    context: Context,
    val topActivityProvider: () -> FragmentActivity?,
    val storage: PermissionStorage = SharedPreferencesPermissionStorage(context.applicationContext),
    val dialogService: PermissionDialogService = DefaultPermissionDialogService,
    val permissions: Collection<RuntimePermission> = emptyList(),
    val routePermissions: Map<String, String> = emptyMap(),
) {
    val appContext: Context = context.applicationContext
}

interface PermissionStorage {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class SharedPreferencesPermissionStorage(context: Context) : PermissionStorage {
    private val preferences = context.getSharedPreferences("vitalhub_permission", Context.MODE_PRIVATE)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}

/** Host applications may replace this with a DialogFragment or their own design system. */
fun interface PermissionDialogService {
    fun showPermissionDenied(
        activity: FragmentActivity,
        permission: RuntimePermission,
        onCancel: () -> Unit,
        onOpenSettings: () -> Unit,
    )
}

object DefaultPermissionDialogService : PermissionDialogService {
    override fun showPermissionDenied(
        activity: FragmentActivity,
        permission: RuntimePermission,
        onCancel: () -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onCancel()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("需要${permission.description}权限")
            .setMessage("请在系统设置中允许${permission.description}权限后重试。")
            .setNegativeButton("取消") { _, _ -> onCancel() }
            .setPositiveButton("前往设置") { _, _ -> onOpenSettings() }
            .setOnCancelListener { onCancel() }
            .show()
    }
}
