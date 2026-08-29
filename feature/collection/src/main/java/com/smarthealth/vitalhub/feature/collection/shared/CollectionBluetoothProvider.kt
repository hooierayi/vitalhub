package com.smarthealth.vitalhub.feature.collection.shared

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val BLUETOOTH_STATE_CONNECTED = 2

data class CollectionBluetoothProviderState(
    val scanning: Boolean = false,
    val scannedDevices: List<BluetoothKitDevice> = emptyList(),
    val lastConnectedDevice: BluetoothKitDevice? = null,
    val connectingDeviceId: String? = null,
    val connectedDeviceId: String? = null,
    val connectionError: String? = null,
)

internal fun isConnectedBluetoothState(managedByBluetoothKit: Boolean, connectState: Int): Boolean =
    managedByBluetoothKit && connectState == BLUETOOTH_STATE_CONNECTED

internal fun BluetoothKitDevice.isConnectedDevice(): Boolean = isConnectedBluetoothState(
    managedByBluetoothKit = BluetoothKit.getInstance().multipleBluetoothController.isContainDevice(this),
    connectState = connectState,
)

internal fun CollectionBluetoothProviderState.knownDevices(): List<BluetoothKitDevice> {
    val devicesById = linkedMapOf<String, BluetoothKitDevice>()
    lastConnectedDevice?.let { devicesById[it.key] = it }
    scannedDevices.forEach { devicesById[it.key] = it }
    return devicesById.values.toList()
}

internal fun CollectionBluetoothProviderState.connectedDevice(): BluetoothKitDevice? =
    connectedDeviceId?.let { connectedId ->
        knownDevices().firstOrNull { it.key == connectedId }
    }

/** Activity-scoped Bluetooth state and operations shared by the collection flow. */
class CollectionBluetoothProvider(
    application: Application,
) : AndroidViewModel(application) {
    private val bluetoothKit = BluetoothKit.getInstance()
    private val deviceProvider = checkNotNull(
        ARouter.getInstance().navigation(DeviceProvider::class.java),
    ) { "DeviceProvider is not registered." }
    private val connectionEvents = Channel<Unit>(Channel.BUFFERED)
    private var scanGeneration = 0

    private val _uiState = MutableStateFlow(
        CollectionBluetoothProviderState(
            lastConnectedDevice = restoreLastConnectedDevice(),
        ).let { initialState ->
            val restoredDevice = initialState.lastConnectedDevice
            initialState.copy(
                connectedDeviceId = restoredDevice
                    ?.takeIf { runCatching(it::isConnectedDevice).getOrDefault(false) }
                    ?.key,
            )
        },
    )
    val uiState: StateFlow<CollectionBluetoothProviderState> = _uiState.asStateFlow()
    val connectionSucceeded = connectionEvents.receiveAsFlow()

    fun stopScan() {
        scanGeneration++
        bluetoothKit.stopScan()
        _uiState.value = _uiState.value.copy(scanning = false)
    }

    fun connect(deviceId: String) {
        if (_uiState.value.connectingDeviceId != null || _uiState.value.connectedDeviceId == deviceId) return
        val device = _uiState.value.scannedDevices.firstOrNull { it.key == deviceId }
            ?: _uiState.value.lastConnectedDevice?.takeIf { it.key == deviceId }
            ?: return

        stopScan()
        _uiState.value = _uiState.value.copy(
            scanning = false,
            connectingDeviceId = deviceId,
            connectionError = null,
        )
        val callback = object : BluetoothConnectCallback {
            override fun onConnectStart() = Unit

            override fun onConnectFailure(bluetoothDevice: BluetoothKitDevice?, error: Exception?) {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        connectingDeviceId = null,
                        connectionError = "连接失败，请确认设备已开启并靠近手机",
                    )
                }
            }

            override fun onConnectSuccess(bluetoothDevice: BluetoothKitDevice) {
                viewModelScope.launch {
                    persistLastConnectedDevice(bluetoothDevice)
                    _uiState.value = _uiState.value.copy(
                        connectingDeviceId = null,
                        connectedDeviceId = bluetoothDevice.key,
                        lastConnectedDevice = bluetoothDevice,
                        connectionError = null,
                    )
                    connectionEvents.send(Unit)
                }
            }

            override fun onDisConnected(bluetoothDevice: BluetoothKitDevice?, active: Boolean) {
                viewModelScope.launch {
                    val disconnectedDeviceId = bluetoothDevice?.key ?: deviceId
                    if (_uiState.value.connectedDeviceId == disconnectedDeviceId) {
                        _uiState.value = _uiState.value.copy(connectedDeviceId = null)
                    }
                }
            }
        }
        device.connect(callback)
    }

    fun disconnect(deviceId: String) {
        val state = _uiState.value
        if (state.connectedDeviceId != deviceId) return
        val device = state.knownDevices().firstOrNull { it.key == deviceId } ?: return
        device.disconnect()
        _uiState.value = state.copy(
            connectedDeviceId = null,
            connectingDeviceId = null,
            connectionError = null,
        )
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_uiState.value.scanning || _uiState.value.connectingDeviceId != null) return
        val generation = ++scanGeneration
        _uiState.value = _uiState.value.copy(scanning = true, connectionError = null)
        bluetoothKit.startLeCan(object : BluetoothScanCallback() {
            override fun onScanResult(bluetoothDevice: BluetoothKitDevice) {
                if (bluetoothDevice.bluetoothDevice?.name.isNullOrBlank()) return
                val address = bluetoothDevice.key.takeIf { it.isNotBlank() } ?: return
                viewModelScope.launch {
                    val devices = _uiState.value.scannedDevices.toMutableList()
                    val existingIndex = devices.indexOfFirst { it.key == address }
                    if (existingIndex >= 0) devices[existingIndex] = bluetoothDevice else devices += bluetoothDevice
                    _uiState.value = _uiState.value.copy(
                        scannedDevices = devices,
                    )
                }
            }

            override fun onScanComplete() = finishScan(generation)

            override fun onScanCancel() = finishScan(generation)
        })
    }

    private fun finishScan(generation: Int) {
        viewModelScope.launch {
            if (generation == scanGeneration) {
                _uiState.value = _uiState.value.copy(scanning = false)
            }
        }
    }

    private fun restoreLastConnectedDevice(): BluetoothKitDevice? = deviceProvider.getCurrentDevice()

    private fun persistLastConnectedDevice(device: BluetoothKitDevice) {
        deviceProvider.saveDevice(device)
    }

    override fun onCleared() {
        bluetoothKit.destroy()
        _uiState.value = _uiState.value.copy(
            scanning = false,
            connectingDeviceId = null,
            connectedDeviceId = null,
        )
        super.onCleared()
    }

}
