package com.smarthealth.vitalhub.feature.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navigation.RouteArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScannedDevice(val id: String, val name: String, val serialNumber: String, val signalDbm: Int, val battery: Int, val storage: Int, val inProject: Boolean)
data class DeviceScanUiState(
    val sessionId: String,
    val scanning: Boolean = true,
    val projectOnly: Boolean = true,
    val devices: List<ScannedDevice> = emptyList(),
    val connectedDeviceId: String? = null,
)

class DeviceScanViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val sampleDevices = listOf(ScannedDevice("ECGR01A123456", "ECG-R01", "ECGR01A123456", -62, 82, 68, true))
    private val _uiState = MutableStateFlow(DeviceScanUiState(savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty(), devices = sampleDevices))
    val uiState: StateFlow<DeviceScanUiState> = _uiState.asStateFlow()

    fun setProjectOnly(enabled: Boolean) { _uiState.value = _uiState.value.copy(projectOnly = enabled) }
    fun refresh() { _uiState.value = _uiState.value.copy(scanning = true, devices = sampleDevices) }
    fun connect(deviceId: String) { _uiState.value = _uiState.value.copy(scanning = false, connectedDeviceId = deviceId) }
}
