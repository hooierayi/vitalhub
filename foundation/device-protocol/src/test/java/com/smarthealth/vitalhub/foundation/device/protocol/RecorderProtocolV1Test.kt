package com.smarthealth.vitalhub.foundation.device.protocol

import com.smarthealth.vitalhub.foundation.device.api.FrameContinuity
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderProtocolV1Test {
    private val engine = RecorderProtocolV1.create(
        RecorderProtocolV1Config(
            scalarByteOrder = ByteOrder.BIG_ENDIAN,
            checksumIncludesHeader = true,
            receiptCodes = setOf(0xF1, 0xF2, 0xF3),
        ),
    )

    @Test
    fun keepsPartialDataAndEmitsParsedAggregateFrame() {
        val frame = dataFrame(sequence = 7)
        assertTrue(engine.feed(byteArrayOf(0x11, 0x22) + frame.copyOfRange(0, 900)).isEmpty())

        val packets = engine.feed(frame.copyOfRange(900, frame.size), receivedAtMillis = 123L)
        val parsed = (packets.single() as ProtocolPacket.Data).frame

        assertEquals(7, parsed.metadata.sequence)
        assertEquals(123L, parsed.metadata.receivedAtMillis)
        assertEquals(FrameContinuity.FIRST, parsed.metadata.continuity)
        assertEquals(-2, parsed.ecg.first())
        assertEquals(-2, parsed.respiration.first())
        assertEquals(36.5, parsed.temperature.skinCelsius, 0.001)
        assertEquals(25.0, parsed.temperature.ambientCelsius, 0.001)
        assertEquals(50.34, parsed.temperature.humidityPercent, 0.001)
    }

    @Test
    fun emitsMultipleFramesAndTracksSequenceGap() {
        val packets = engine.feed(dataFrame(10) + dataFrame(12))
        assertEquals(2, packets.size)
        assertEquals(
            FrameContinuity.GAP,
            (packets[1] as ProtocolPacket.Data).frame.metadata.continuity,
        )
    }

    @Test
    fun recoversFromBadChecksumAndFindsFollowingReceipt() {
        val damaged = receipt(0xF1).also { it[it.lastIndex] = 0 }
        val packets = engine.feed(damaged + receipt(0xF2))
        val receipt = packets.single() as ProtocolPacket.Receipt
        assertEquals(0xF2, receipt.code)
        assertEquals(0, receipt.status)
    }

    @Test
    fun onlyConfiguredResponseCodesAreDecodedAsReceipts() {
        val packet = engine.feed(receipt(0xF4)).single()
        assertTrue(packet is ProtocolPacket.UnknownControl)
        assertEquals(0xF4, (packet as ProtocolPacket.UnknownControl).code)
    }

    @Test
    fun parsesSignedBigEndianEcgAndRespirationLimits() {
        val frame = dataFrame(sequence = 8).also { bytes ->
            put16(bytes, 4, 0x8000)
            put16(bytes, 6, 0x7FFF)
            bytes[504] = 0x80.toByte()
            bytes[505] = 0x00
            bytes[506] = 0x00
            bytes[507] = 0x7F
            bytes[508] = 0xFF.toByte()
            bytes[509] = 0xFF.toByte()
            applyChecksum(bytes)
        }

        val parsed = (engine.feed(frame).single() as ProtocolPacket.Data).frame

        assertEquals(-32_768, parsed.ecg[0])
        assertEquals(32_767, parsed.ecg[1])
        assertEquals(-8_388_608, parsed.respiration[0])
        assertEquals(8_388_607, parsed.respiration[1])
    }

    @Test
    fun parsesTemperatureAndHumidityAsSignedBigEndianValues() {
        val frame = dataFrame(sequence = 9).also { bytes ->
            put16(bytes, 1_254, -999)
            put16(bytes, 1_256, -250)
            put16(bytes, 1_258, -1)
            applyChecksum(bytes)
        }

        val parsed = (engine.feed(frame).single() as ProtocolPacket.Data).frame

        assertEquals(-9.99, parsed.temperature.skinCelsius, 0.001)
        assertEquals(-2.50, parsed.temperature.ambientCelsius, 0.001)
        assertEquals(-0.01, parsed.temperature.humidityPercent, 0.001)
    }

    private fun dataFrame(sequence: Int): ByteArray {
        val bytes = ByteArray(1_323)
        bytes[0] = 0xAA.toByte()
        bytes[1] = 0xAA.toByte()
        put16(bytes, 2, sequence)
        put16(bytes, 4, 0xFFFE)
        bytes[504] = 0xFF.toByte()
        bytes[505] = 0xFF.toByte()
        bytes[506] = 0xFE.toByte()
        put16(bytes, 1_254, 3_650)
        put16(bytes, 1_256, 2_500)
        put16(bytes, 1_258, 5_034)
        applyChecksum(bytes)
        return bytes
    }

    private fun receipt(code: Int): ByteArray = byteArrayOf(
        0x5A,
        0xA5.toByte(),
        code.toByte(),
        0x06,
        0x00,
        0x00,
    ).also(::applyChecksum)

    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun applyChecksum(bytes: ByteArray) {
        var xor = 0
        for (index in 0 until bytes.lastIndex) xor = xor xor (bytes[index].toInt() and 0xFF)
        bytes[bytes.lastIndex] = xor.toByte()
    }
}
