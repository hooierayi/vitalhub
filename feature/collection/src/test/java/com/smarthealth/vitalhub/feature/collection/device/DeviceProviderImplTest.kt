package com.smarthealth.vitalhub.feature.collection.device

import android.os.Parcelable
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProviderImplTest {
    @Test
    fun `returns null before a device is stored`() {
        assertNull(createProvider(FakeDeviceStorage()).getCurrentDevice())
    }

    @Test
    fun `stores and restores the last connected device`() {
        val provider = createProvider(FakeDeviceStorage())
        val device = ProviderTestBluetoothGattDevice(
            deviceKey = "AA:BB",
            scanRecord = byteArrayOf(0x01, 0x7f, 0xff.toByte()),
            rssi = -61,
            timestampNanos = 1_234L,
        )

        assertTrue(provider.saveDevice(device))

        val restoredDevice = provider.getCurrentDevice() as BluetoothGattDevice
        assertEquals(device.key, restoredDevice.key)
        assertNull(restoredDevice.scanRecord)
        assertEquals(0, restoredDevice.rssi)
        assertEquals(0L, restoredDevice.timestampNanos)
    }

    private fun createProvider(storage: KVStorage): DeviceProviderImpl =
        DeviceProviderImpl(storage) { address -> ProviderTestBluetoothGattDevice(address) }
}

private class ProviderTestBluetoothGattDevice(
    private val deviceKey: String,
    scanRecord: ByteArray? = null,
    rssi: Int = 0,
    timestampNanos: Long = 0L,
) : BluetoothGattDevice(null, scanRecord, rssi, timestampNanos) {
    override fun getKey(): String = deviceKey
    override fun getConnectState(): Int = 0
}

private class FakeDeviceStorage : KVStorage {
    private val values = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = key in values
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
    override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue
    override fun getDouble(key: String, defaultValue: Double): Double = values[key] as? Double ?: defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
    override fun getString(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
    override fun <T : Parcelable> getParcelable(key: String, clazz: Class<T>, defaultValue: T?): T? = defaultValue
    override fun edit(): KVStorage.Editor = Editor(values)

    private class Editor(private val values: MutableMap<String, Any>) : KVStorage.Editor {
        override fun remove(key: String) = apply { values.remove(key) }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putDouble(key: String, value: Double) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putString(key: String, value: String?) = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun putParcelable(key: String, value: Parcelable?) = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun clear() = apply { values.clear() }
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }
}
