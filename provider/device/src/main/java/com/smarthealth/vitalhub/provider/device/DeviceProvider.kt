package com.smarthealth.vitalhub.provider.device

import com.alibaba.android.arouter.facade.template.IProvider
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice

/** Provides the most recently connected collection device across feature boundaries. */
interface DeviceProvider : IProvider {

    fun getCurrentDevice(): BluetoothKitDevice?

    fun getCurrentDeviceAddress(): String?

    /** Returns the persisted name of the most recently connected device when available. */
    fun getCurrentDeviceName(): String?

    fun saveDevice(device: BluetoothKitDevice): Boolean
}
