package com.smarthealth.vitalhub.foundation.device.protocol

import com.smarthealth.vitalhub.foundation.device.api.FrameContinuity
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface ProtocolPacket {
    data class Data(val frame: RecorderFrame) : ProtocolPacket
    data class Receipt(val code: Int, val status: Int) : ProtocolPacket
    data class UnknownControl(val code: Int, val payload: ByteArray) : ProtocolPacket
}

data class ProtocolIssue(
    val reason: String,
    val skippedBytes: Int,
)

interface ProtocolEngine {
    val issues: Flow<ProtocolIssue>
    fun feed(bytes: ByteArray, receivedAtMillis: Long = System.currentTimeMillis()): List<ProtocolPacket>
    fun reset()
}

fun interface ProtocolEngineFactory {
    fun create(): ProtocolEngine
}

enum class CandidateType { DATA, COMMAND }

class DecodeContext internal constructor(
    val buffer: ReadOnlyRingBuffer,
    val receivedAtMillis: Long,
) {
    var type: CandidateType? = null
    var frameLength: Int = 0
    var candidate: ByteArray? = null
    var continuity: FrameContinuity = FrameContinuity.FIRST
}

sealed interface DecodeDecision {
    data object NeedMoreData : DecodeDecision
    data class Emit(val packet: ProtocolPacket, val consumedBytes: Int) : DecodeDecision
    data class Recover(val skipBytes: Int, val reason: String) : DecodeDecision
}

interface ProtocolInterceptor {
    fun intercept(chain: Chain): DecodeDecision

    interface Chain {
        val context: DecodeContext
        fun proceed(): DecodeDecision
    }
}

class ProtocolPipeline(
    private val interceptors: List<ProtocolInterceptor>,
) {
    init {
        require(interceptors.isNotEmpty())
    }

    fun decode(context: DecodeContext): DecodeDecision = RealChain(context, 0).proceed()

    private inner class RealChain(
        override val context: DecodeContext,
        private val index: Int,
    ) : ProtocolInterceptor.Chain {
        override fun proceed(): DecodeDecision {
            check(index < interceptors.size) { "Protocol pipeline has no terminal decoder" }
            return interceptors[index].intercept(RealChain(context, index + 1))
        }
    }
}

class ProtocolStreamEngine(
    private val pipeline: ProtocolPipeline,
    initialBufferCapacity: Int = 2_048,
    maxBufferCapacity: Int = 256 * 1_024,
    private val trace: DeviceTrace = DeviceTrace.NONE,
) : ProtocolEngine {
    private val buffer = ExpandableRingByteBuffer(initialBufferCapacity, maxBufferCapacity)
    private val mutableIssues = MutableSharedFlow<ProtocolIssue>(extraBufferCapacity = 32)
    override val issues: Flow<ProtocolIssue> = mutableIssues.asSharedFlow()

    override fun feed(bytes: ByteArray, receivedAtMillis: Long): List<ProtocolPacket> {
        val sizeBefore = buffer.size
        val capacityBefore = buffer.capacity
        buffer.write(bytes)
        trace.log(
            "BUFFER",
            "write=${bytes.size}, size=$sizeBefore->${buffer.size}, capacity=$capacityBefore->${buffer.capacity}",
        )
        val packets = mutableListOf<ProtocolPacket>()
        while (buffer.size > 0) {
            val context = DecodeContext(ReadOnlyRingBuffer(buffer), receivedAtMillis)
            when (val decision = pipeline.decode(context)) {
                DecodeDecision.NeedMoreData -> {
                    trace.log("PROTOCOL", "need-more type=${context.type}, buffered=${buffer.size}")
                    break
                }
                is DecodeDecision.Emit -> {
                    require(decision.consumedBytes in 1..buffer.size)
                    val packetBytes = buffer.copy(0, decision.consumedBytes)
                    buffer.skip(decision.consumedBytes)
                    packets += decision.packet
                    trace.log("PROTOCOL", "emit=${decision.packet.summary()}, consumed=${decision.consumedBytes}, remaining=${buffer.size}")
                    trace.log(
                        "PROTOCOL_DATA_RAW",
                        "decoded ${packetBytes.size} bytes",
                        packetBytes,
                    )
                }
                is DecodeDecision.Recover -> {
                    val skip = decision.skipBytes.coerceIn(1, buffer.size)
                    buffer.skip(skip)
                    mutableIssues.tryEmit(ProtocolIssue(decision.reason, skip))
                    trace.log("RECOVERY", "reason=${decision.reason}, skipped=$skip, remaining=${buffer.size}")
                }
            }
        }
        return packets
    }

    override fun reset() {
        trace.log("BUFFER", "reset discarded=${buffer.size}")
        buffer.clear()
    }

    private fun ProtocolPacket.summary(): String = when (this) {
        is ProtocolPacket.Data -> "data seq=${frame.metadata.sequence}, continuity=${frame.metadata.continuity}"
        is ProtocolPacket.Receipt -> "receipt code=0x${code.toString(16).uppercase()}, status=$status"
        is ProtocolPacket.UnknownControl -> "unknown-control code=0x${code.toString(16).uppercase()}, bytes=${payload.size}"
    }
}
