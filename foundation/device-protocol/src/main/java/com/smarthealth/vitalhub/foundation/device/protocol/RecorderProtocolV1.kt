package com.smarthealth.vitalhub.foundation.device.protocol

import com.smarthealth.vitalhub.foundation.device.api.FrameContinuity
import com.smarthealth.vitalhub.foundation.device.api.FrameMetadata
import com.smarthealth.vitalhub.foundation.device.api.MotionSample
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.SweatLevel
import com.smarthealth.vitalhub.foundation.device.api.TemperatureBlock
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import java.nio.ByteOrder

data class RecorderProtocolV1Config(
    val scalarByteOrder: ByteOrder,
    val receiptCodes: Set<Int>,
    val dataFrameLength: Int = 1_323,
    val checksumIncludesHeader: Boolean = true,
    val protocolVersion: Int = 1,
)

object RecorderProtocolV1 {
    fun create(config: RecorderProtocolV1Config, trace: DeviceTrace = DeviceTrace.NONE): ProtocolEngine {
        val sequence = SequenceContinuityInterceptor()
        return ProtocolStreamEngine(
            ProtocolPipeline(
                listOf(
                    HeaderSyncInterceptor(),
                    FrameBoundaryInterceptor(config.dataFrameLength),
                    XorIntegrityInterceptor(config.checksumIncludesHeader),
                    sequence,
                    RecorderPacketDecoder(config),
                ),
            ),
            trace = trace,
        )
    }
}

private class HeaderSyncInterceptor : ProtocolInterceptor {
    private val dataHeader = byteArrayOf(0xAA.toByte(), 0xAA.toByte())
    private val commandHeader = byteArrayOf(0x5A, 0xA5.toByte())

    override fun intercept(chain: ProtocolInterceptor.Chain): DecodeDecision {
        val buffer = chain.context.buffer
        if (buffer.size < 2) return DecodeDecision.NeedMoreData
        val offset = buffer.indexOf(dataHeader, commandHeader)
        if (offset < 0) {
            return DecodeDecision.Recover(buffer.size - 1, "No known frame header")
        }
        if (offset > 0) return DecodeDecision.Recover(offset, "Discard bytes before frame header")
        chain.context.type = if (buffer[0] == dataHeader[0]) CandidateType.DATA else CandidateType.COMMAND
        return chain.proceed()
    }
}

private class FrameBoundaryInterceptor(
    private val dataFrameLength: Int,
) : ProtocolInterceptor {
    override fun intercept(chain: ProtocolInterceptor.Chain): DecodeDecision {
        val context = chain.context
        val length = when (context.type) {
            CandidateType.DATA -> dataFrameLength
            CandidateType.COMMAND -> {
                if (context.buffer.size < 4) return DecodeDecision.NeedMoreData
                context.buffer.unsigned(3)
            }
            null -> return DecodeDecision.Recover(1, "Frame type was not selected")
        }
        if (length < 5) return DecodeDecision.Recover(1, "Invalid frame length: $length")
        if (context.buffer.size < length) return DecodeDecision.NeedMoreData
        context.frameLength = length
        context.candidate = context.buffer.copy(0, length)
        return chain.proceed()
    }
}

private class XorIntegrityInterceptor(
    private val includesHeader: Boolean,
) : ProtocolInterceptor {
    override fun intercept(chain: ProtocolInterceptor.Chain): DecodeDecision {
        val candidate = checkNotNull(chain.context.candidate)
        val start = if (includesHeader) 0 else 2
        var xor = 0
        for (index in start until candidate.size) xor = xor xor (candidate[index].toInt() and 0xFF)
        return if (xor == 0) chain.proceed() else DecodeDecision.Recover(1, "XOR checksum mismatch")
    }
}

private class SequenceContinuityInterceptor : ProtocolInterceptor {
    private var previous: Int? = null

    override fun intercept(chain: ProtocolInterceptor.Chain): DecodeDecision {
        if (chain.context.type != CandidateType.DATA) return chain.proceed()
        val candidate = checkNotNull(chain.context.candidate)
        val sequence = ((candidate[2].toInt() and 0xFF) shl 8) or
            (candidate[3].toInt() and 0xFF)
        chain.context.continuity = classify(sequence)
        return chain.proceed().also { decision ->
            if (decision is DecodeDecision.Emit) {
                if (decision.packet is ProtocolPacket.Data) previous = sequence
            }
        }
    }

    fun classify(sequence: Int): FrameContinuity {
        val last = previous ?: return FrameContinuity.FIRST
        val delta = (sequence - last) and 0xFFFF
        return when {
            delta == 0 -> FrameContinuity.DUPLICATE
            delta == 1 -> FrameContinuity.CONTINUOUS
            delta in 2..0x7FFF -> FrameContinuity.GAP
            else -> FrameContinuity.OUT_OF_ORDER
        }
    }
}

private class RecorderPacketDecoder(
    private val config: RecorderProtocolV1Config,
) : ProtocolInterceptor {
    override fun intercept(chain: ProtocolInterceptor.Chain): DecodeDecision {
        val context = chain.context
        val candidate = checkNotNull(context.candidate)
        val packet = when (context.type) {
            CandidateType.DATA -> ProtocolPacket.Data(
                parseData(candidate, context.receivedAtMillis, context.continuity),
            )
            CandidateType.COMMAND -> parseCommand(candidate)
            null -> return DecodeDecision.Recover(1, "Frame type was not selected")
        }
        return DecodeDecision.Emit(packet, context.frameLength)
    }

    private fun parseCommand(bytes: ByteArray): ProtocolPacket {
        val code = unsigned(bytes, 2)
        val status = if (bytes.size > 4) unsigned(bytes, 4) else -1
        return if (code in config.receiptCodes) {
            ProtocolPacket.Receipt(code, status)
        } else {
            ProtocolPacket.UnknownControl(code, bytes.copyOfRange(4, bytes.lastIndex))
        }
    }

    private fun parseData(
        bytes: ByteArray,
        receivedAtMillis: Long,
        continuity: FrameContinuity,
    ): RecorderFrame {
        require(bytes.size == config.dataFrameLength)
        val sequence = unsigned16(bytes, 2)
        var offset = 4
        val ecg = IntArray(250) { readShort(bytes, offset).toInt().also { offset += 2 } }
        val respiration = IntArray(250) { readSigned24(bytes, offset).also { offset += 3 } }
        val temperature = TemperatureBlock(
            skinCelsius = readShort(bytes, offset).also { offset += 2 } / 100.0,
            ambientCelsius = readShort(bytes, offset).also { offset += 2 } / 100.0,
            humidityPercent = readShort(bytes, offset).also { offset += 2 } / 100.0,
        )
        val motion = List(5) {
            MotionSample(
                gyroX = readShort(bytes, offset).also { offset += 2 },
                gyroY = readShort(bytes, offset).also { offset += 2 },
                gyroZ = readShort(bytes, offset).also { offset += 2 },
                accelerationX = readShort(bytes, offset).also { offset += 2 },
                accelerationY = readShort(bytes, offset).also { offset += 2 },
                accelerationZ = readShort(bytes, offset).also { offset += 2 },
            )
        }
        val sweat = when (unsigned(bytes, offset++)) {
            1 -> SweatLevel.NONE
            2 -> SweatLevel.LIGHT
            3 -> SweatLevel.MEDIUM
            4 -> SweatLevel.HEAVY
            else -> SweatLevel.UNKNOWN
        }
        val leadOff = unsigned(bytes, offset) != 0
        return RecorderFrame(
            metadata = FrameMetadata(
                sequence = sequence,
                receivedAtMillis = receivedAtMillis,
                continuity = continuity,
                protocolVersion = config.protocolVersion,
            ),
            ecg = ecg,
            respiration = respiration,
            temperature = temperature,
            motion = motion,
            sweatLevel = sweat,
            leadOff = leadOff,
        )
    }

    private fun readShort(bytes: ByteArray, offset: Int): Short = unsigned16(bytes, offset).toShort()

    private fun unsigned16(bytes: ByteArray, offset: Int): Int = if (config.scalarByteOrder == ByteOrder.BIG_ENDIAN) {
        (unsigned(bytes, offset) shl 8) or unsigned(bytes, offset + 1)
    } else {
        unsigned(bytes, offset) or (unsigned(bytes, offset + 1) shl 8)
    }

    private fun readSigned24(bytes: ByteArray, offset: Int): Int {
        val value = if (config.scalarByteOrder == ByteOrder.BIG_ENDIAN) {
            (unsigned(bytes, offset) shl 16) or (unsigned(bytes, offset + 1) shl 8) or unsigned(bytes, offset + 2)
        } else {
            unsigned(bytes, offset) or (unsigned(bytes, offset + 1) shl 8) or (unsigned(bytes, offset + 2) shl 16)
        }
        return if (value and 0x800000 != 0) value or -0x1000000 else value
    }

    private fun unsigned(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF
}
