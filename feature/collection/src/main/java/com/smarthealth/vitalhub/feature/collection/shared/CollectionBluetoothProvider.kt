package com.smarthealth.vitalhub.feature.collection.shared

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback
import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.sdk.ProtocolByteOrder
import com.smarthealth.vitalhub.foundation.device.sdk.RecorderDeviceSdk
import com.smarthealth.vitalhub.foundation.device.sdk.RecorderDeviceSdkConfig
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    private val deviceSession = RecorderDeviceSdk.create(
        RecorderDeviceSdkConfig(
            serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
            notifyCharacteristicUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
            writeCharacteristicUuid = "0000fff2-0000-1000-8000-00805f9b34fb",
            scalarByteOrder = ProtocolByteOrder.BIG_ENDIAN,
            checksumIncludesHeader = true,
            useNotificationDescriptor = false,
            dataChainTraceEnabled = true,
        ),
    ).createSession()
    private val deviceProvider = checkNotNull(
        ARouter.getInstance().navigation(DeviceProvider::class.java),
    ) { "DeviceProvider is not registered." }
    private val connectionEvents = Channel<Unit>(Channel.BUFFERED)
    private val mutableConnectionLostEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var scanGeneration = 0

    private val _uiState = MutableStateFlow(
        CollectionBluetoothProviderState(
            lastConnectedDevice = restoreLastConnectedDevice(),
        ),
    )
    val uiState: StateFlow<CollectionBluetoothProviderState> = _uiState.asStateFlow()
    val connectionSucceeded = connectionEvents.receiveAsFlow()
    val connectionLostEvents: SharedFlow<Unit> = mutableConnectionLostEvents
    val frames = deviceSession.frames
    val ecgWaveforms = deviceSession.ecgWaveforms
    val respirationWaveforms = deviceSession.respirationWaveforms
    val metrics = deviceSession.metrics
    val recordingState = deviceSession.recordingState
    val diagnostics = deviceSession.diagnostics

    init {
        viewModelScope.launch {
            deviceSession.connected.drop(1).collect { connected ->
                if (!connected && _uiState.value.connectedDeviceId != null) {
                    _uiState.value = _uiState.value.copy(connectedDeviceId = null)
                    mutableConnectionLostEvents.tryEmit(Unit)
                }
            }
        }
    }

    fun stopScan() {
        scanGeneration++
        bluetoothKit.stopScan()
        _uiState.value = _uiState.value.copy(scanning = false)
    }

    fun connect(deviceId: String) {
        connect(
            deviceId = deviceId,
            navigateOnSuccess = true,
            restartCollection = true,
            rejectedAction = "启动",
        )
    }

    fun reconnect(restartCollection: Boolean): Boolean {
        val deviceId = _uiState.value.lastConnectedDevice?.key?.takeIf { it.isNotBlank() }
            ?: return false
        connect(
            deviceId = deviceId,
            navigateOnSuccess = false,
            restartCollection = restartCollection,
            rejectedAction = "恢复",
        )
        return true
    }

    private fun connect(
        deviceId: String,
        navigateOnSuccess: Boolean,
        restartCollection: Boolean,
        rejectedAction: String,
    ) {
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
        viewModelScope.launch {
            runCatching { deviceSession.connect(deviceId) }
                .mapCatching {
                    if (restartCollection) {
                        when (val result = deviceSession.execute(DeviceCommand.StartCollection)) {
                            CommandResult.Success -> Unit
                            is CommandResult.Rejected -> error(
                                "设备拒绝${rejectedAction}采集，状态码=${result.status}",
                            )
                            is CommandResult.Failed -> throw result.cause
                        }
                    }
                }
                .onSuccess {
                    persistLastConnectedDevice(device)
                    _uiState.value = _uiState.value.copy(
                        connectingDeviceId = null,
                        connectedDeviceId = deviceId,
                        lastConnectedDevice = device,
                        connectionError = null,
                    )
                    if (navigateOnSuccess) connectionEvents.send(Unit)
                }
                .onFailure { error ->
                    runCatching { deviceSession.disconnect() }
                    _uiState.value = _uiState.value.copy(
                        connectingDeviceId = null,
                        connectedDeviceId = null,
                        connectionError = error.message
                            ?: "连接失败，请确认设备已开启并靠近手机",
                    )
                }
        }
    }

    fun disconnect(deviceId: String) {
        val state = _uiState.value
        if (state.connectedDeviceId != deviceId) return
        viewModelScope.launch {
            runCatching { deviceSession.execute(DeviceCommand.StopCollection) }
            runCatching { deviceSession.disconnect() }
            _uiState.value = state.copy(
                connectedDeviceId = null,
                connectingDeviceId = null,
                connectionError = null,
            )
        }
    }

    suspend fun execute(command: DeviceCommand): CommandResult = deviceSession.execute(command)

    suspend fun startRecording(targetPath: String) = deviceSession.startRecording(targetPath)

    suspend fun stopRecording() = deviceSession.stopRecording()

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
        bluetoothKit.stopScan()
        runBlocking { deviceSession.close() }
        _uiState.value = _uiState.value.copy(
            scanning = false,
            connectingDeviceId = null,
            connectedDeviceId = null,
        )
        super.onCleared()
    }

}
