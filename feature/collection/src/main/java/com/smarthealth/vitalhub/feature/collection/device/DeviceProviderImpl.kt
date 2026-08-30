package com.smarthealth.vitalhub.feature.collection.device

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.core.storage.Storage
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.provider.device.DeviceInfo
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import com.smarthealth.vitalhub.provider.device.DeviceRecordInfo

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

    override fun getDeviceInfo(): DeviceInfo? = requireStorage().getParcelable(
        KEY_DEVICE_INFO,
        DeviceInfo::class.java,
    )

    override fun getRecordInfo(): DeviceRecordInfo? = getDeviceInfo()?.record

    override fun saveDevice(deviceInfo: DeviceInfo): Boolean {
        require(deviceInfo.address.isNotBlank()) { "Device address must not be blank." }
        val existing = getDeviceInfo()
        val persistedDeviceInfo = if (
            deviceInfo.record == null && existing?.address == deviceInfo.address
        ) {
            deviceInfo.copy(record = existing.record)
        } else {
            deviceInfo
        }
        persistedDeviceInfo.record?.let { record ->
            require(record.id.isNotBlank()) { "Device record id must not be blank." }
            require(record.startedAtEpochMillis > 0L) { "Device record start time must be positive." }
        }
        return requireStorage().edit()
            .putParcelable(KEY_DEVICE_INFO, persistedDeviceInfo)
            .commit()
    }

    override fun getCurrentDevice(): BluetoothKitDevice? = getCurrentDeviceAddress()
        ?.let(requireDeviceFactory())

    override fun getCurrentDeviceAddress(): String? = getDeviceInfo()?.address

    override fun getCurrentDeviceName(): String? = getDeviceInfo()?.name

    private fun requireStorage(): KVStorage = checkNotNull(storage) {
        "DeviceProviderImpl must be initialized by ARouter before use."
    }

    private fun requireDeviceFactory() = checkNotNull(deviceFactory) {
        "DeviceProviderImpl must be initialized by ARouter before use."
    }

    private companion object {
        const val STORAGE_ID = "collection_device"
        const val KEY_DEVICE_INFO = "device_info"
    }
}
