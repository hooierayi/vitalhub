package com.smarthealth.vitalhub.foundation.device.api

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceDebugTraceTest {
    @Before
    fun setUp() {
        DeviceDebugTrace.setEnabled(true)
        DeviceDebugTrace.clear()
    }

    @After
    fun tearDown() {
        DeviceDebugTrace.setEnabled(false)
    }

    @Test
    fun keepsPayloadCopyAndFiltersByStage() {
        val payload = byteArrayOf(1, 2, 3)
        DeviceDebugTrace.record("BLE_RX_RAW", "received", payload)
        payload[0] = 9
        DeviceDebugTrace.record("COMMAND", "queued")

        val bluetooth = DeviceDebugTrace.snapshot(setOf("BLE_RX_RAW"))

        assertEquals(1, bluetooth.size)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(bluetooth.single().payload))
    }

    @Test
    fun clearOnlyRemovesRequestedStages() {
        DeviceDebugTrace.record("GATT", "connected")
        DeviceDebugTrace.record("COMMAND", "queued")

        DeviceDebugTrace.clear(setOf("GATT"))

        assertTrue(DeviceDebugTrace.snapshot(setOf("GATT")).isEmpty())
        assertEquals(1, DeviceDebugTrace.snapshot(setOf("COMMAND")).size)
    }

    @Test
    fun rawTrafficDoesNotEvictGattEvents() {
        DeviceDebugTrace.record("GATT", "connect address=AA:BB:CC:DD:EE:FF")
        repeat(600) { index ->
            DeviceDebugTrace.record("BLE_RX_RAW", "received", byteArrayOf(index.toByte()))
        }

        val gatt = DeviceDebugTrace.snapshot(setOf("GATT"))

        assertEquals(1, gatt.size)
        assertEquals("connect address=AA:BB:CC:DD:EE:FF", gatt.single().message)
    }

    @Test
    fun keepsLatestEventForEachStageWhenSharedBufferOverflows() {
        DeviceDebugTrace.record("COMMAND", "queued=StartRecording")
        repeat(600) { index ->
            DeviceDebugTrace.record("PROTOCOL", "emit=$index")
        }

        val protocol = DeviceDebugTrace.snapshot(setOf("COMMAND", "PROTOCOL"))

        assertTrue(protocol.any { it.stage == "COMMAND" && it.message == "queued=StartRecording" })
        assertTrue(protocol.any { it.stage == "PROTOCOL" && it.message == "emit=599" })
    }
}
