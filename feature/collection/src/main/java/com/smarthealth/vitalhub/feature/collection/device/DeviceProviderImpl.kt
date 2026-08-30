package com.smarthealth.vitalhub.feature.collection.device

import android.annotation.SuppressLint
import android.content.Context
import android.bluetooth.BluetoothManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.core.storage.Storage
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.provider.device.DeviceProvider

/** Stores the last successfully connected collection device. */
@Route(path = Routes.DEVICE_PROVIDER)
class DeviceProviderImpl() : DeviceProvider {
    @Volatile
    private var storage: KVStorage? = null
    private var deviceFactory: ((address: String) -> BluetoothKitDevice?)? = null

    internal constructor(
        storage: KVStorage,
        deviceFactory: (address: String) -> BluetoothKitDevice?,
    ) : this() {
        this.storage = storage
        this.deviceFactory = deviceFactory
    }

    override fun init(context: Context) {
        storage = Storage.create(context.applicationContext, STORAGE_ID)
        val bluetoothAdapter = BluetoothKit.getInstance().bluetoothAdapter
        deviceFactory = { address ->
            runCatching {
                BluetoothGattDevice(bluetoothAdapter.getRemoteDevice(address))
            }.getOrNull()
        }
    }

    override fun getCurrentDevice(): BluetoothKitDevice? {
        val currentStorage = requireStorage()
        val mac = currentStorage.getString(KEY_DEVICE_MAC)?.takeIf(String::isNotBlank) ?: return null
        return requireDeviceFactory().invoke(mac)
    }

    override fun getCurrentDeviceName(): String? = requireStorage()
        .getString(KEY_DEVICE_NAME)
        ?.takeIf(String::isNotBlank)

    @SuppressLint("MissingPermission")
    override fun saveDevice(device: BluetoothKitDevice): Boolean {
        require(device.key.isNotBlank()) { "Device id must not be blank." }
        return requireStorage().edit()
            .putString(KEY_DEVICE_MAC, device.key)
            .putString(KEY_DEVICE_NAME, device.bluetoothDevice?.name)
            .commit()
    }

    private fun requireStorage(): KVStorage = checkNotNull(storage) {
        "DeviceProviderImpl must be initialized by ARouter before use."
    }

    private fun requireDeviceFactory() = checkNotNull(deviceFactory) {
        "DeviceProviderImpl must be initialized by ARouter before use."
    }

    private companion object {
        const val STORAGE_ID = "collection_device"
        const val KEY_DEVICE_MAC = "evice_mac"
        const val KEY_DEVICE_NAME = "device_name"
    }
}
