package com.smarthealth.vitalhub.foundation.device.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface DeviceSession {
    val connected: StateFlow<Boolean>
    val frames: SharedFlow<RecorderFrame>
    val ecgWaveforms: Flow<EcgWaveformFrame>
    val respirationWaveforms: Flow<RespirationWaveformFrame>
    val metrics: StateFlow<DeviceMetrics?>
    val recordingState: StateFlow<RecordingState>
    val diagnostics: Flow<String>

    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun execute(command: DeviceCommand): CommandResult
    suspend fun startRecording(targetPath: String)
    suspend fun stopRecording()
    suspend fun close()
}

fun interface DeviceSdk {
    fun createSession(): DeviceSession
}
