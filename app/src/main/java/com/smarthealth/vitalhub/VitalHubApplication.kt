package com.smarthealth.vitalhub

import android.app.Application
import android.content.pm.ApplicationInfo
import com.alibaba.android.arouter.launcher.ARouter

class VitalHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            ARouter.openLog()
            ARouter.openDebug()
        }
        ARouter.init(this)
    }
}
