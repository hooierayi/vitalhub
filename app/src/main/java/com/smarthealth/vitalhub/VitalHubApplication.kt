package com.smarthealth.vitalhub

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.permission.PermissionManager
import com.smarthealth.vitalhub.core.permission.PermissionServiceInitConfig
import com.smarthealth.vitalhub.core.permission.model.RuntimePermission
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.bluetooth.scan.BluetoothScanRulesConfig

class VitalHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(CurrentActivityHolder)
        BluetoothKit.Builder()
            .setScanRulesConfig(
                BluetoothScanRulesConfig.Builder()
                    .setFilter { true }
                    .build(),
            )
            .init()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            ARouter.openLog()
            ARouter.openDebug()
        }
        ARouter.init(this)
        PermissionManager.init(
            PermissionServiceInitConfig(
                context = this,
                topActivityProvider = { CurrentActivityHolder.activity },
                permissions = listOf(
                    RuntimePermission(
                        id = DEVICE_BLUETOOTH_PERMISSION,
                        description = "附近设备",
                        requestCode = 1001,
                        permissionsProvider = { sdkInt ->
                            if (sdkInt >= android.os.Build.VERSION_CODES.S) {
                                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                    ),
                ),
                routePermissions = mapOf(Routes.COLLECTION_FLOW to DEVICE_BLUETOOTH_PERMISSION),
            ),
        )
    }

    private companion object {
        const val DEVICE_BLUETOOTH_PERMISSION = "device.bluetooth"
    }
}
