package com.smarthealth.vitalhub.foundation.device.command

import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RecorderCommandEncoderTest {
    private val encoder = RecorderCommandEncoder()

    @Test
    fun encodesStartAndStopFramesFromProtocolExamples() {
        assertArrayEquals(
            byteArrayOf(0x5A, 0xA5.toByte(), 0x01, 0x05, 0xFB.toByte()),
            encoder.encode(DeviceCommand.StartCollection),
        )
        assertArrayEquals(
            byteArrayOf(0x5A, 0xA5.toByte(), 0x02, 0x05, 0xF8.toByte()),
            encoder.encode(DeviceCommand.StopCollection),
        )
    }
}
