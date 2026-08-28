package com.smarthealth.vitalhub

import android.content.Context
import androidx.fragment.app.Fragment
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.annotation.Interceptor
import com.alibaba.android.arouter.facade.callback.InterceptorCallback
import com.alibaba.android.arouter.facade.template.IInterceptor
import com.smarthealth.vitalhub.core.navi.FlowNavigationHost
import com.smarthealth.vitalhub.core.navi.FlowNavigationRequest
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.core.permission.PermissionManager
import com.smarthealth.vitalhub.core.permission.PermissionServiceProvider
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission

/** Applies dynamically configured permission guards before protected Fragment routes are displayed. */
@Interceptor(priority = 10)
class PermissionRouteInterceptor : IInterceptor {
    override fun init(context: Context) = Unit

    override fun process(postcard: Postcard, callback: InterceptorCallback) {
        val permission = PermissionManager.permissionForRoute(postcard.path)
        if (permission == null) {
            callback.onContinue(postcard)
            return
        }
        PermissionManager.requestPermission(permission, object : PermissionServiceProvider.RequestCallback {
            override fun onPermissionGranted(permission: RuntimePermission) {
                // ARouter's navigation(context) does not write that Context back to Postcard.
                // Use the same foreground Activity source injected into PermissionManager instead.
                val host = CurrentActivityHolder.activity as? FlowNavigationHost
                val fragment = runCatching {
                    postcard.destination.getDeclaredConstructor().newInstance() as Fragment
                }.getOrNull()
                if (host == null || fragment == null) {
                    callback.onInterrupt(IllegalStateException("Protected route requires a Fragment FlowNavigationHost."))
                    return
                }
                fragment.arguments = postcard.extras
                host.show(
                    FlowNavigationRequest(
                        key = postcard.extras.getString(RouteArgs.NAVIGATION_KEY).orEmpty(),
                        fragment = fragment,
                        addToBackStack = postcard.extras.getBoolean(RouteArgs.ADD_TO_BACK_STACK, true),
                        clearBackStack = postcard.extras.getBoolean(RouteArgs.CLEAR_BACK_STACK, false),
                    ),
                )
                // ARouter does not return Fragment instances after async interception. The host above
                // owns the real transaction; continuing only completes ARouter's interceptor chain.
                callback.onContinue(postcard)
            }

            override fun onPermissionDenied(permission: RuntimePermission) {
                callback.onInterrupt(SecurityException("${permission.description} permission was denied."))
            }
        })
    }
}
