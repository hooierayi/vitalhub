package com.smarthealth.vitalhub.core.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission

/** Android system interaction, isolated from the configurable service and permission models. */
object SystemPermissionChecker {
    fun checkSelfPermission(context: Context, permission: RuntimePermission): Boolean =
        permission.currentPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun shouldShowRequestPermissionRationale(activity: FragmentActivity, permission: RuntimePermission): Boolean =
        permission.currentPermissions().any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    fun request(
        activity: FragmentActivity,
        permission: RuntimePermission,
        callback: PermissionServiceProvider.RequestCallback,
    ) {
        CheckFragment.findOrAttach(activity).requestPermission(permission, callback)
    }

    private class CheckFragment : Fragment() {
        private data class PendingRequest(
            val permission: RuntimePermission,
            val callback: PermissionServiceProvider.RequestCallback,
        )

        private val pendingRequests = mutableMapOf<Int, PendingRequest>()

        fun requestPermission(permission: RuntimePermission, callback: PermissionServiceProvider.RequestCallback) {
            check(!pendingRequests.containsKey(permission.requestCode)) {
                "Permission requestCode ${permission.requestCode} is already in use."
            }
            pendingRequests[permission.requestCode] = PendingRequest(permission, callback)
            requestPermissions(permission.currentPermissions().toTypedArray(), permission.requestCode)
        }

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            val request = pendingRequests.remove(requestCode) ?: return
            if (SystemPermissionChecker.checkSelfPermission(requireContext(), request.permission)) {
                request.callback.onPermissionGranted(request.permission)
            } else {
                request.callback.onPermissionDenied(request.permission)
            }
        }

        companion object {
            private const val TAG = "vitalhub.permission.check"

            fun findOrAttach(activity: FragmentActivity): CheckFragment {
                val manager = activity.supportFragmentManager
                check(!manager.isStateSaved) { "Cannot request a permission after FragmentManager state is saved." }
                return manager.findFragmentByTag(TAG) as? CheckFragment
                    ?: CheckFragment().also { manager.beginTransaction().add(it, TAG).commitNow() }
            }
        }
    }
}
