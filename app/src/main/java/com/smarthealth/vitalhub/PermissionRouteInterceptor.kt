package com.smarthealth.vitalhub

import android.content.Context
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.annotation.Interceptor
import com.alibaba.android.arouter.facade.callback.InterceptorCallback
import com.alibaba.android.arouter.facade.template.IInterceptor
import com.smarthealth.vitalhub.core.permission.PermissionManager
import com.smarthealth.vitalhub.core.permission.PermissionServiceProvider
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission

/** Applies dynamically configured permission guards before protected Activity routes are opened. */
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
                callback.onContinue(postcard)
            }

            override fun onPermissionDenied(permission: RuntimePermission) {
                callback.onInterrupt(SecurityException("${permission.description} permission was denied."))
            }
        })
    }
}
