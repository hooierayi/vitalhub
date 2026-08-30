package com.smarthealth.vitalhub.foundation.device.command

import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import java.nio.charset.Charset

fun interface DeviceCommandEncoder {
    fun encode(command: DeviceCommand): ByteArray
}

class RecorderCommandEncoder(
    private val nameCharset: Charset = Charset.forName("GBK"),
) : DeviceCommandEncoder {
    override fun encode(command: DeviceCommand): ByteArray = when (command) {
        DeviceCommand.StartCollection -> frame(0x01, byteArrayOf())
        DeviceCommand.StopCollection -> frame(0x02, byteArrayOf())
        is DeviceCommand.StartContinuous -> {
            val subject = command.subject
            require(subject.genderCode in 0..255)
            require(subject.age in 0..255)
            require(subject.year in 1980..2235)
            require(subject.month in 1..12 && subject.day in 1..31)
            require(subject.hour in 0..23 && subject.minute in 0..59 && subject.second in 0..59)
            val name = fixedWidthName(subject.name, 10)
            frame(
                0x03,
                name + byteArrayOf(
                    subject.genderCode.toByte(),
                    subject.age.toByte(),
                    (subject.year - 1980).toByte(),
                    subject.month.toByte(),
                    subject.day.toByte(),
                    subject.hour.toByte(),
                    subject.minute.toByte(),
                    subject.second.toByte(),
                ),
            )
        }
    }

    private fun fixedWidthName(value: String, width: Int): ByteArray {
        val result = ByteArray(width)
        var offset = 0
        for (character in value) {
            val encoded = character.toString().toByteArray(nameCharset)
            if (offset + encoded.size > width) break
            encoded.copyInto(result, destinationOffset = offset)
            offset += encoded.size
        }
        return result
    }

    private fun frame(code: Int, payload: ByteArray): ByteArray {
        val result = ByteArray(5 + payload.size)
        result[0] = 0x5A
        result[1] = 0xA5.toByte()
        result[2] = code.toByte()
        result[3] = result.size.toByte()
        payload.copyInto(result, destinationOffset = 4)
        var checksum = 0
        for (index in 0 until result.lastIndex) checksum = checksum xor (result[index].toInt() and 0xFF)
        result[result.lastIndex] = checksum.toByte()
        return result
    }
}
