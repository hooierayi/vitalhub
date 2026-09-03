package com.smarthealth.vitalhub.feature.collection.shared

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback
import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.sdk.ProtocolByteOrder
import com.smarthealth.vitalhub.foundation.device.sdk.RecorderDeviceSdk
import com.smarthealth.vitalhub.foundation.device.sdk.RecorderDeviceSdkConfig
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrameSink
import com.smarthealth.vitalhub.foundation.file.protocol.DefaultContinuousDicomWriterFactory
import com.smarthealth.vitalhub.foundation.file.protocol.DicomPublisher
import com.smarthealth.vitalhub.foundation.file.protocol.DicomRecordingDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.DicomRecordingResult
import com.smarthealth.vitalhub.foundation.file.protocol.DicomWorkspace
import com.smarthealth.vitalhub.feature.collection.recording.CollectionDicomRecorder
import com.smarthealth.vitalhub.provider.device.DeviceInfo
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

enum class DeviceConnectionOperation {
    CONNECTING,
    DISCONNECTING,
}

data class CollectionBluetoothProviderState(
    val scanning: Boolean = false,
    val scannedDevices: List<BluetoothKitDevice> = emptyList(),
    val lastConnectedDevice: BluetoothKitDevice? = null,
    val deviceOperation: DeviceConnectionOperation? = null,
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
    private val dicomRecorder = CollectionDicomRecorder(DefaultContinuousDicomWriterFactory())
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

    /** Device-page connection: connects the session and publishes the event that opens preview. */
    fun connect(deviceId: String) {
        val state = _uiState.value
        if (
            state.deviceOperation != null ||
            state.connectedDeviceId == deviceId
        ) return
        val device = findKnownDevice(deviceId) ?: return
        markConnecting()
        viewModelScope.launch {
            runCatching { deviceSession.connect(deviceId) }
                .onSuccess {
                    markConnected(device)
                    connectionEvents.send(Unit)
                }
                .onFailure { error -> markConnectionFailed(error) }
        }
    }

    /** Disconnect-dialog reconnection: optionally restores data, but never publishes navigation. */
    fun reconnectFromDisconnectDialog(restartCollection: Boolean): Boolean {
        val deviceId = _uiState.value.lastConnectedDevice?.key?.takeIf { it.isNotBlank() }
            ?: return false
        if (_uiState.value.deviceOperation != null) return true
        val device = findKnownDevice(deviceId) ?: return false
        markConnecting()
        viewModelScope.launch {
            runCatching { deviceSession.connect(deviceId) }
                .mapCatching {
                    if (restartCollection) {
                        requireSuccessfulCommand(
                            command = DeviceCommand.StartCollection,
                            rejectedAction = "恢复",
                        )
                    }
                }
                .onSuccess { markConnected(device) }
                .onFailure { error -> markConnectionFailed(error) }
        }
        return true
    }

    private fun findKnownDevice(deviceId: String): BluetoothKitDevice? =
        _uiState.value.scannedDevices.firstOrNull { it.key == deviceId }
            ?: _uiState.value.lastConnectedDevice?.takeIf { it.key == deviceId }

    private fun markConnecting() {
        stopScan()
        _uiState.value = _uiState.value.copy(
            scanning = false,
            deviceOperation = DeviceConnectionOperation.CONNECTING,
            connectionError = null,
        )
    }

    private fun markConnected(device: BluetoothKitDevice) {
        persistLastConnectedDevice(device)
        _uiState.value = _uiState.value.copy(
            deviceOperation = null,
            connectedDeviceId = device.key,
            lastConnectedDevice = device,
            connectionError = null,
        )
    }

    private suspend fun requireSuccessfulCommand(
        command: DeviceCommand,
        rejectedAction: String,
    ) {
        when (val result = deviceSession.execute(command)) {
            CommandResult.Success -> Unit
            is CommandResult.Rejected -> error(
                "设备拒绝${rejectedAction}采集，状态码=${result.status}",
            )
            is CommandResult.Failed -> throw result.cause
        }
    }

    private suspend fun markConnectionFailed(error: Throwable) {
        runCatching { deviceSession.disconnect() }
        _uiState.value = _uiState.value.copy(
            deviceOperation = null,
            connectedDeviceId = null,
            connectionError = error.message
                ?: "连接失败，请确认设备已开启并靠近手机",
        )
    }

    fun disconnect(deviceId: String) {
        val state = _uiState.value
        if (
            state.connectedDeviceId != deviceId ||
            state.deviceOperation != null
        ) return
        _uiState.value = state.copy(
            deviceOperation = DeviceConnectionOperation.DISCONNECTING,
            connectionError = null,
        )
        viewModelScope.launch {
            runCatching { deviceSession.disconnect() }
            _uiState.value = _uiState.value.copy(
                connectedDeviceId = null,
                deviceOperation = null,
                connectionError = null,
            )
        }
    }

    suspend fun execute(command: DeviceCommand): CommandResult = deviceSession.execute(command)

    /** Disconnects the app while leaving device-side continuous recording untouched. */
    suspend fun disconnectKeepingContinuousRecording(): Boolean {
        val state = _uiState.value
        val connectedDeviceId = state.connectedDeviceId ?: return true
        if (state.deviceOperation != null) return false
        _uiState.value = state.copy(
            deviceOperation = DeviceConnectionOperation.DISCONNECTING,
            connectedDeviceId = null,
            connectionError = null,
        )
        return runCatching { deviceSession.disconnect() }
            .fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        deviceOperation = null,
                        connectionError = null,
                    )
                    true
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        deviceOperation = null,
                        connectedDeviceId = connectedDeviceId,
                        connectionError = error.message ?: "蓝牙断开失败",
                    )
                    false
                },
            )
    }

    suspend fun startRecording(
        definition: DicomRecordingDefinition,
        workspace: DicomWorkspace,
        publisher: DicomPublisher,
        maximumFrameCount: Long? = null,
    ) {
        require(maximumFrameCount == null || maximumFrameCount > 0) {
            "maximumFrameCount must be positive when specified"
        }
        dicomRecorder.start(definition, workspace, publisher)
        try {
            var appendedFrameCount = 0L
            deviceSession.startRecording(RecorderFrameSink { frame ->
                if (maximumFrameCount == null || appendedFrameCount < maximumFrameCount) {
                    dicomRecorder.append(frame)
                    appendedFrameCount++
                }
            })
        } catch (error: Throwable) {
            dicomRecorder.abort(retainWorkspace = false)
            throw error
        }
    }

    suspend fun stopRecording(): DicomRecordingResult? {
        deviceSession.stopRecording()
        return dicomRecorder.stop()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (
            _uiState.value.scanning ||
            _uiState.value.deviceOperation != null
        ) return
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

    @SuppressLint("MissingPermission")
    private fun restoreLastConnectedDevice(): BluetoothKitDevice? = deviceProvider.getCurrentDevice()

    @SuppressLint("MissingPermission")
    private fun persistLastConnectedDevice(device: BluetoothKitDevice) {
        deviceProvider.saveDevice(
            DeviceInfo(
                address = device.key,
                name = device.bluetoothDevice?.name,
            ),
        )
    }

    override fun onCleared() {
        bluetoothKit.stopScan()
        runBlocking {
            runCatching { deviceSession.stopRecording() }
            runCatching { dicomRecorder.abort(retainWorkspace = true) }
            deviceSession.close()
        }
        _uiState.value = _uiState.value.copy(
            scanning = false,
            deviceOperation = null,
            connectedDeviceId = null,
        )
        super.onCleared()
    }

}
