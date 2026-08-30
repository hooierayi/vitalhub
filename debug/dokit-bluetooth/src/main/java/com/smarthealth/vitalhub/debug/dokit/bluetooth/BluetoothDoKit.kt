package com.smarthealth.vitalhub.debug.dokit.bluetooth

import android.app.Activity
import android.content.Context
import com.didichuxing.doraemonkit.DoKit
import com.didichuxing.doraemonkit.kit.AbstractKit
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugTrace

class BluetoothDoKit : AbstractKit() {
    override val name: Int = R.string.dokit_bluetooth_name
    override val icon: Int = R.drawable.ic_dokit_bluetooth

    override fun onClickWithReturn(activity: Activity): Boolean {
        DoKit.launchFloating(BluetoothDebugDoKitView::class.java)
        return true
    }

    override fun onAppInit(context: Context?) {
        DeviceDebugTrace.setEnabled(true)
    }
}
