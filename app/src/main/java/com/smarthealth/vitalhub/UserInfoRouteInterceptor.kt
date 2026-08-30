package com.smarthealth.vitalhub

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.annotation.Interceptor
import com.alibaba.android.arouter.facade.callback.InterceptorCallback
import com.alibaba.android.arouter.facade.template.IInterceptor
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.provider.user.UserInfoProvider

/** Redirects collection routes to profile editing until a complete user profile exists. */
@Interceptor(priority = 5)
class UserInfoRouteInterceptor : IInterceptor {
    private lateinit var applicationContext: Context

    override fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    override fun process(postcard: Postcard, callback: InterceptorCallback) {
        if (postcard.path != Routes.COLLECTION_FLOW || hasUserInfo()) {
            callback.onContinue(postcard)
            return
        }

        val navigationContext = CurrentActivityHolder.activity ?: applicationContext
        Handler(Looper.getMainLooper()).post {
            Navigator.editUserInfo(navigationContext)
        }
        callback.onInterrupt(IllegalStateException("User information is required before collection."))
    }

    private fun hasUserInfo(): Boolean = runCatching {
        ARouter.getInstance().navigation(UserInfoProvider::class.java)?.getUser() != null
    }.getOrDefault(false)
}
