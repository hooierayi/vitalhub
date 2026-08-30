package com.smarthealth.vitalhub.foundation.device.sdk

import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.api.DeviceMetrics
import com.smarthealth.vitalhub.foundation.device.api.DeviceSdk
import com.smarthealth.vitalhub.foundation.device.api.DeviceSession
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import com.smarthealth.vitalhub.foundation.device.api.EcgWaveformFrame
import com.smarthealth.vitalhub.foundation.device.api.RecordingState
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.RespirationWaveformFrame
import com.smarthealth.vitalhub.foundation.device.command.DeviceCommandEncoder
import com.smarthealth.vitalhub.foundation.device.command.SerialCommandExecutor
import com.smarthealth.vitalhub.foundation.device.protocol.ProtocolEngineFactory
import com.smarthealth.vitalhub.foundation.device.protocol.ProtocolPacket
import com.smarthealth.vitalhub.foundation.device.storage.FrameRecorderFactory
import com.smarthealth.vitalhub.foundation.device.transport.DeviceTransportFactory
import com.smarthealth.vitalhub.foundation.device.transport.TransportState
import com.smarthealth.vitalhub.foundation.device.waveform.WaveformPipelineFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow

class DefaultDeviceSdk(
    private val transportFactory: DeviceTransportFactory,
    private val protocolFactory: ProtocolEngineFactory,
    private val commandEncoder: DeviceCommandEncoder,
    private val recorderFactory: FrameRecorderFactory,
    private val waveformFactory: WaveformPipelineFactory,
    private val trace: DeviceTrace = DeviceTrace.NONE,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceSdk {
    override fun createSession(): DeviceSession = DefaultDeviceSession(
        transportFactory = transportFactory,
        protocolFactory = protocolFactory,
        commandEncoder = commandEncoder,
        recorderFactory = recorderFactory,
        waveformFactory = waveformFactory,
        trace = trace,
        dispatcher = dispatcher,
    )
}

private class DefaultDeviceSession(
    transportFactory: DeviceTransportFactory,
    protocolFactory: ProtocolEngineFactory,
    commandEncoder: DeviceCommandEncoder,
    recorderFactory: FrameRecorderFactory,
    waveformFactory: WaveformPipelineFactory,
    private val trace: DeviceTrace,
    dispatcher: CoroutineDispatcher,
) : DeviceSession {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val transport = transportFactory.create()
    private val protocol = protocolFactory.create()
    private val recorder = recorderFactory.create(scope)
    private val waveform = waveformFactory.create()
    private val commands = SerialCommandExecutor(transport, commandEncoder, scope, trace = trace)
    private val mutableFrames = MutableSharedFlow<RecorderFrame>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutableMetrics = MutableStateFlow<DeviceMetrics?>(null)
    private val mutableDiagnostics = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override val connected: StateFlow<Boolean> = transport.state
        .map { it == TransportState.CONNECTED }
        .stateIn(scope, SharingStarted.Eagerly, false)
    override val frames: SharedFlow<RecorderFrame> = mutableFrames.asSharedFlow()
    override val ecgWaveforms: Flow<EcgWaveformFrame> = waveform.ecgFrames
    override val respirationWaveforms: Flow<RespirationWaveformFrame> = waveform.respirationFrames
    override val metrics: StateFlow<DeviceMetrics?> = mutableMetrics.asStateFlow()
    override val recordingState: StateFlow<RecordingState> = recorder.state
    override val diagnostics: Flow<String> = mutableDiagnostics.asSharedFlow()

    init {
        scope.launch {
            transport.incomingBytes.collect { bytes ->
                protocol.feed(bytes).forEach { packet ->
                    when (packet) {
                        is ProtocolPacket.Data -> distribute(packet.frame)
                        is ProtocolPacket.Receipt -> {
                            trace.log("DISPATCH", "receipt -> command queue code=0x${packet.code.toString(16).uppercase()}")
                            commands.accept(packet.code, packet.status)
                        }
                        is ProtocolPacket.UnknownControl -> mutableDiagnostics.emit(
                            "Unknown control packet: code=0x${packet.code.toString(16)}",
                        )
                    }
                }
            }
        }
        scope.launch {
            protocol.issues.collect { issue ->
                mutableDiagnostics.emit(
                    "Protocol recovery: ${issue.reason}, skipped=${issue.skippedBytes}",
                )
            }
        }
        scope.launch {
            transport.state.collect { state ->
                if (state == TransportState.DISCONNECTED) {
                    commands.cancelAll(IllegalStateException("Device transport is not connected"))
                }
            }
        }
    }

    override suspend fun connect(address: String) {
        trace.log("SESSION", "connect address=$address")
        protocol.reset()
        transport.connect(address)
    }

    override suspend fun disconnect() {
        trace.log("SESSION", "disconnect")
        commands.cancelAll()
        if (recordingState.value is RecordingState.Recording) recorder.stop()
        transport.disconnect()
        protocol.reset()
        mutableMetrics.value = null
    }

    override suspend fun execute(command: DeviceCommand): CommandResult {
        check(transport.state.value == TransportState.CONNECTED) { "Device is not connected" }
        return commands.execute(command)
    }

    override suspend fun startRecording(targetPath: String) = recorder.start(targetPath)

    override suspend fun stopRecording() = recorder.stop()

    override suspend fun close() {
        disconnect()
        scope.cancel()
    }

    private suspend fun distribute(frame: RecorderFrame) {
        waveform.accept(frame)
        if (recorder.state.value is RecordingState.Recording) recorder.append(frame)
        mutableMetrics.value = DeviceMetrics(
            sequence = frame.metadata.sequence,
            skinCelsius = frame.temperature.skinCelsius,
            ambientCelsius = frame.temperature.ambientCelsius,
            humidityPercent = frame.temperature.humidityPercent,
            sweatLevel = frame.sweatLevel,
            leadOff = frame.leadOff,
        )
        mutableFrames.emit(frame)
        trace.log(
            "DISPATCH",
            "seq=${frame.metadata.sequence} -> waveform, metrics, app-flow, file=${recorder.state.value is RecordingState.Recording}",
        )
    }
}
