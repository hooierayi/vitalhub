package com.smarthealth.vitalhub.feature.collection

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProviderState
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CollectionFlowHomeUiState(
    val sessionId: String,
    val scanning: Boolean = false,
    val projectOnly: Boolean = true,
    val scannedDevices: List<BluetoothKitDevice> = emptyList(),
    val deviceOrderIds: List<String> = emptyList(),
    val lastConnectedDevice: BluetoothKitDevice? = null,
    val selectedAvailableDeviceId: String? = null,
    val connectingDeviceId: String? = null,
    val connectedDeviceId: String? = null,
    val connectionError: String? = null,
    val flowError: String? = null,
)

@SuppressLint("MissingPermission")
internal fun BluetoothKitDevice.isProjectDevice(): Boolean =
    isProjectDeviceName(bluetoothDevice?.name)

internal fun isProjectDeviceName(name: String?): Boolean =
    name?.uppercase(Locale.ROOT)?.replace("-", "")?.startsWith("HJ") == true

internal fun CollectionFlowHomeUiState.orderedKnownDevices(): List<BluetoothKitDevice> {
    val devicesById = linkedMapOf<String, BluetoothKitDevice>()
    lastConnectedDevice?.let { devicesById[it.key] = it }
    scannedDevices.forEach { devicesById[it.key] = it }
    val orderedIds = (deviceOrderIds + devicesById.keys).distinct()
    return orderedIds.mapNotNull(devicesById::get)
}

internal fun CollectionFlowHomeUiState.visibleScannedDevices(): List<BluetoothKitDevice> =
    orderedKnownDevices().filter { !projectOnly || it.isProjectDevice() }

internal fun CollectionFlowHomeUiState.availableDevice(): BluetoothKitDevice? =
    selectedAvailableDeviceId
        ?.let { selectedId -> orderedKnownDevices().firstOrNull { it.key == selectedId } }
        ?: lastConnectedDevice?.let { remembered ->
            orderedKnownDevices().firstOrNull { it.key == remembered.key } ?: remembered
        }
        ?: visibleScannedDevices().firstOrNull()

internal fun CollectionFlowHomeUiState.otherDevices(): List<BluetoothKitDevice> {
    val availableDeviceId = availableDevice()?.key
    return visibleScannedDevices().filterNot { it.key == availableDeviceId }
}

internal fun CollectionFlowHomeUiState.withSelectedAvailableDevice(deviceId: String): CollectionFlowHomeUiState {
    val currentAvailableDevice = availableDevice() ?: return this
    if (connectedDeviceId == currentAvailableDevice.key) return this
    val currentAvailableId = currentAvailableDevice.key
    if (currentAvailableId == deviceId) return this
    val orderedIds = (deviceOrderIds + orderedKnownDevices().map { it.key })
        .distinct()
        .toMutableList()
    val currentIndex = orderedIds.indexOf(currentAvailableId)
    val targetIndex = orderedIds.indexOf(deviceId)
    if (currentIndex < 0 || targetIndex < 0) return this
    orderedIds[currentIndex] = deviceId
    orderedIds[targetIndex] = currentAvailableId
    return copy(
        selectedAvailableDeviceId = deviceId,
        deviceOrderIds = orderedIds,
    )
}

internal fun CollectionFlowHomeUiState.withBluetoothState(
    bluetoothState: CollectionBluetoothProviderState,
): CollectionFlowHomeUiState = copy(
    scanning = bluetoothState.scanning,
    scannedDevices = bluetoothState.scannedDevices,
    lastConnectedDevice = bluetoothState.lastConnectedDevice,
    connectingDeviceId = bluetoothState.connectingDeviceId,
    connectedDeviceId = bluetoothState.connectedDeviceId,
    connectionError = bluetoothState.connectionError,
)

/** Page-only state for filtering, card selection/order, and flow feedback. */
class CollectionFlowHomeViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CollectionFlowHomeUiState(
            sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty(),
        ),
    )
    val uiState: StateFlow<CollectionFlowHomeUiState> = _uiState.asStateFlow()

    fun setProjectOnly(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(projectOnly = enabled)
    }

    fun selectAvailableDevice(deviceId: String, bluetoothState: CollectionBluetoothProviderState) {
        val combinedState = _uiState.value.withBluetoothState(bluetoothState)
        if (combinedState.connectingDeviceId != null) return
        if (combinedState.availableDevice()?.key == combinedState.connectedDeviceId) return
        if (combinedState.scannedDevices.none { it.key == deviceId }) return
        val selectedState = combinedState.withSelectedAvailableDevice(deviceId)
        _uiState.value = _uiState.value.copy(
            selectedAvailableDeviceId = selectedState.selectedAvailableDeviceId,
            deviceOrderIds = selectedState.deviceOrderIds,
        )
    }

    fun reportFlowError() {
        _uiState.value = _uiState.value.copy(flowError = "采集流程暂不可用，请稍后重试")
    }
}
