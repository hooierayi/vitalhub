package com.smarthealth.vitalhub.foundation.device.waveform

import com.smarthealth.vitalhub.foundation.device.api.EcgWaveformFrame
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.RespirationWaveformFrame
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface WaveformPipeline {
    val ecgFrames: Flow<EcgWaveformFrame>
    val respirationFrames: Flow<RespirationWaveformFrame>
    fun accept(frame: RecorderFrame)
}

class DefaultWaveformPipeline(
    bufferCapacity: Int = 4,
    private val trace: DeviceTrace = DeviceTrace.NONE,
) : WaveformPipeline {
    private val mutableEcgFrames = MutableSharedFlow<EcgWaveformFrame>(
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutableRespirationFrames = MutableSharedFlow<RespirationWaveformFrame>(
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val ecgFrames: Flow<EcgWaveformFrame> = mutableEcgFrames.asSharedFlow()
    override val respirationFrames: Flow<RespirationWaveformFrame> = mutableRespirationFrames.asSharedFlow()

    override fun accept(frame: RecorderFrame) {
        val ecgEmitted = mutableEcgFrames.tryEmit(
            EcgWaveformFrame(
                sequence = frame.metadata.sequence,
                samples = frame.ecg,
                continuity = frame.metadata.continuity,
            ),
        )
        val respirationEmitted = mutableRespirationFrames.tryEmit(
            RespirationWaveformFrame(
                sequence = frame.metadata.sequence,
                samples = frame.respiration,
                continuity = frame.metadata.continuity,
            ),
        )
        trace.log(
            "WAVEFORM",
            "seq=${frame.metadata.sequence}, ecg=${frame.ecg.size}, respiration=${frame.respiration.size}, " +
                "ecgEmitted=$ecgEmitted, respirationEmitted=$respirationEmitted",
        )
    }
}

fun interface WaveformPipelineFactory {
    fun create(): WaveformPipeline
}
