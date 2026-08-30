package com.smarthealth.vitalhub.feature.collection

import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProviderState
import com.smarthealth.vitalhub.feature.collection.shared.DeviceConnectionOperation
import com.smarthealth.vitalhub.feature.collection.shared.isConnectedBluetoothState
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies connection-home filtering, partitioning, and card selection. */
class CollectionFlowHomeUiStateTest {
    private val projectDevice = ScanStateTestBluetoothKitDevice("01")
    private val secondProjectDevice = ScanStateTestBluetoothKitDevice("02")
    private val thirdProjectDevice = ScanStateTestBluetoothKitDevice("04")
    private val fourthProjectDevice = ScanStateTestBluetoothKitDevice("05")
    private val unrelatedDevice = ScanStateTestBluetoothKitDevice("03")

    @Test
    fun `first visible scan is available when there is no connection history`() {
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice, unrelatedDevice, secondProjectDevice),
        )

        assertEquals(projectDevice, state.availableDevice())
    }

    @Test
    fun `last connected device stays available before it is scanned`() {
        val rememberedDevice = ScanStateTestBluetoothKitDevice("remembered")
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice),
            lastConnectedDevice = rememberedDevice,
        )

        assertEquals(rememberedDevice, state.availableDevice())
    }

    @Test
    fun `selected scanned device replaces the current available device`() {
        val rememberedDevice = ScanStateTestBluetoothKitDevice("remembered")
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice, secondProjectDevice),
            lastConnectedDevice = rememberedDevice,
            selectedAvailableDeviceId = secondProjectDevice.key,
        )

        assertEquals(secondProjectDevice, state.availableDevice())
    }

    @Test
    fun `available and other devices form mutually exclusive scan partitions`() {
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice, unrelatedDevice, secondProjectDevice),
        )

        assertEquals(projectDevice, state.availableDevice())
        assertEquals(listOf(unrelatedDevice, secondProjectDevice), state.otherDevices())
    }

    @Test
    fun `switching available device returns previous scanned device to others`() {
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice, secondProjectDevice),
            selectedAvailableDeviceId = secondProjectDevice.key,
        )

        assertEquals(secondProjectDevice, state.availableDevice())
        assertEquals(listOf(projectDevice), state.otherDevices())
    }

    @Test
    fun `switching devices replaces only the selected other-device slot`() {
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(
                projectDevice,
                secondProjectDevice,
                thirdProjectDevice,
                fourthProjectDevice,
            ),
            deviceOrderIds = listOf("01", "02", "04", "05"),
        )

        val switchedState = state.withSelectedAvailableDevice(thirdProjectDevice.key)

        assertEquals(thirdProjectDevice, switchedState.availableDevice())
        assertEquals(
            listOf(secondProjectDevice, projectDevice, fourthProjectDevice),
            switchedState.otherDevices(),
        )
    }

    @Test
    fun `remembered available device keeps the clicked other-device slot stable`() {
        val rememberedDevice = ScanStateTestBluetoothKitDevice("remembered")
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice, secondProjectDevice, thirdProjectDevice),
            deviceOrderIds = listOf("remembered", "01", "02", "04"),
            lastConnectedDevice = rememberedDevice,
        )

        val switchedState = state.withSelectedAvailableDevice(secondProjectDevice.key)

        assertEquals(secondProjectDevice, switchedState.availableDevice())
        assertEquals(
            listOf(projectDevice, rememberedDevice, thirdProjectDevice),
            switchedState.otherDevices(),
        )
    }

    @Test
    fun `turning project filter off exposes every scan result`() {
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(projectDevice, unrelatedDevice),
        )

        assertEquals(listOf(projectDevice, unrelatedDevice), state.visibleScannedDevices())
        assertEquals(listOf(unrelatedDevice), state.otherDevices())
    }

    @Test
    fun `project filter uses the bluetooth device name`() {
        assertEquals(true, isProjectDeviceName("HJ-A"))
        assertEquals(false, isProjectDeviceName("Headphones"))
        assertEquals(false, isProjectDeviceName(null))
    }

    @Test
    fun `home state keeps page fields when bluetooth state is combined`() {
        val homeState = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            selectedAvailableDeviceId = "selected",
            flowError = "flow error",
        )
        val combinedState = homeState.withBluetoothState(
            CollectionBluetoothProviderState(
                scanning = true,
                scannedDevices = listOf(projectDevice),
                deviceOperation = DeviceConnectionOperation.DISCONNECTING,
                connectionError = "connection error",
            ),
        )

        assertEquals(false, combinedState.projectOnly)
        assertEquals("selected", combinedState.selectedAvailableDeviceId)
        assertEquals("flow error", combinedState.flowError)
        assertEquals(true, combinedState.scanning)
        assertEquals(listOf(projectDevice), combinedState.scannedDevices)
        assertEquals(DeviceConnectionOperation.DISCONNECTING, combinedState.deviceOperation)
        assertEquals("connection error", combinedState.connectionError)
    }

    @Test
    fun `restored device status uses its current bluetooth connection state`() {
        assertEquals(true, isConnectedBluetoothState(managedByBluetoothKit = true, connectState = 2))
        assertEquals(false, isConnectedBluetoothState(managedByBluetoothKit = false, connectState = 2))
        assertEquals(false, isConnectedBluetoothState(managedByBluetoothKit = true, connectState = 0))
    }

    @Test
    fun `connected available device cannot be replaced`() {
        val connectedDevice = ScanStateTestBluetoothKitDevice(
            deviceKey = "connected",
            connectState = 2,
        )
        val state = CollectionFlowHomeUiState(
            sessionId = "session",
            projectOnly = false,
            scannedDevices = listOf(connectedDevice, secondProjectDevice),
            connectedDeviceId = connectedDevice.key,
        )

        assertEquals(state, state.withSelectedAvailableDevice(secondProjectDevice.key))
    }
}

private class ScanStateTestBluetoothKitDevice(
    private val deviceKey: String,
    private val connectState: Int = 0,
) : BluetoothKitDevice(null) {
    override fun getKey(): String = deviceKey
    override fun getConnectState(): Int = connectState
}
