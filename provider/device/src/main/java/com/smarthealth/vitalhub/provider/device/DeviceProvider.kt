package com.smarthealth.vitalhub.provider.device

import com.alibaba.android.arouter.facade.template.IProvider
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice

/** Provides the most recently connected collection device across feature boundaries. */
interface DeviceProvider : IProvider {

    fun getDeviceInfo(): DeviceInfo?

    fun getRecordInfo(): DeviceRecordInfo?

    /**
     * Saves device information. When [DeviceInfo.record] is null, the existing record is
     * preserved only if the stored device has the same address.
     */
    fun saveDevice(deviceInfo: DeviceInfo): Boolean

    /** Compatibility API. Prefer [getDeviceInfo] for new code. */
    fun getCurrentDevice(): BluetoothKitDevice?

    /** Compatibility API. Prefer [getDeviceInfo] for new code. */
    fun getCurrentDeviceAddress(): String?

    /** Compatibility API. Prefer [getDeviceInfo] for new code. */
    fun getCurrentDeviceName(): String?
}
