package com.smarthealth.vitalhub.foundation.device.storage

import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.RecordingState
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface FrameRecorder {
    val state: StateFlow<RecordingState>
    suspend fun start(targetPath: String)
    suspend fun append(frame: RecorderFrame)
    suspend fun stop()
}

class BinaryFrameRecorder(
    scope: CoroutineScope,
    queueCapacity: Int = 128,
    private val trace: DeviceTrace = DeviceTrace.NONE,
) : FrameRecorder {
    private val commands = Channel<RecorderCommand>(queueCapacity)
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = mutableState.asStateFlow()

    init {
        scope.launch { runWriter() }
    }

    override suspend fun start(targetPath: String) {
        val completion = CompletableDeferred<Unit>()
        commands.send(RecorderCommand.Start(targetPath, completion))
        completion.await()
    }

    override suspend fun append(frame: RecorderFrame) {
        commands.send(RecorderCommand.Append(frame))
    }

    override suspend fun stop() {
        val completion = CompletableDeferred<Unit>()
        commands.send(RecorderCommand.Stop(completion))
        completion.await()
    }

    private suspend fun runWriter() {
        var output: DataOutputStream? = null
        var partFile: File? = null
        var targetFile: File? = null
        var recordingFailed = false
        try {
            for (command in commands) {
                try {
                    when (command) {
                    is RecorderCommand.Start -> {
                        check(output == null) { "Recording is already active" }
                        val target = File(command.targetPath)
                        check(!target.exists()) { "Recording target already exists: ${target.path}" }
                        target.parentFile?.mkdirs()
                        val part = File(target.parentFile, "${target.name}.part")
                        check(!part.exists()) { "Partial recording already exists: ${part.path}" }
                        output = DataOutputStream(BufferedOutputStream(FileOutputStream(part)))
                        output.write(byteArrayOf('V'.code.toByte(), 'H'.code.toByte(), 'F'.code.toByte(), 1))
                        partFile = part
                        targetFile = target
                        recordingFailed = false
                        mutableState.value = RecordingState.Recording(target.path)
                        trace.log("FILE", "started path=${target.path}")
                        command.completion.complete(Unit)
                    }
                    is RecorderCommand.Append -> output?.let {
                        writeFrame(it, command.frame)
                        trace.log("FILE", "append seq=${command.frame.metadata.sequence}")
                    }
                    is RecorderCommand.Stop -> {
                        output?.flush()
                        output?.close()
                        output = null
                        val part = partFile
                        val target = targetFile
                        if (!recordingFailed && part != null && target != null) {
                            check(part.renameTo(target)) { "Unable to finalize recording: ${target.path}" }
                            mutableState.value = RecordingState.Completed(target.path)
                            trace.log("FILE", "completed path=${target.path}, bytes=${target.length()}")
                        } else if (!recordingFailed) {
                            mutableState.value = RecordingState.Idle
                            trace.log("FILE", "stop ignored, no active file")
                        }
                        partFile = null
                        targetFile = null
                        recordingFailed = false
                        command.completion.complete(Unit)
                    }
                }
                } catch (error: Throwable) {
                    output?.close()
                    output = null
                    recordingFailed = true
                    mutableState.value = RecordingState.Failed(error)
                    trace.log("FILE", "failure=${error.message}")
                    when (command) {
                        is RecorderCommand.Start -> command.completion.completeExceptionally(error)
                        is RecorderCommand.Stop -> command.completion.completeExceptionally(error)
                        is RecorderCommand.Append -> Unit
                    }
                }
            }
        } finally {
            output?.close()
        }
    }

    private fun writeFrame(output: DataOutputStream, frame: RecorderFrame) {
        output.writeInt(frame.metadata.sequence)
        output.writeLong(frame.metadata.receivedAtMillis)
        output.writeByte(frame.metadata.continuity.ordinal)
        output.writeByte(frame.metadata.protocolVersion)
        output.writeShort(frame.ecg.size)
        frame.ecg.forEach { output.writeShort(it.toInt()) }
        output.writeShort(frame.respiration.size)
        frame.respiration.forEach(output::writeInt)
        output.writeDouble(frame.temperature.skinCelsius)
        output.writeDouble(frame.temperature.ambientCelsius)
        output.writeDouble(frame.temperature.humidityPercent)
        output.writeByte(frame.motion.size)
        frame.motion.forEach { sample ->
            output.writeShort(sample.gyroX.toInt())
            output.writeShort(sample.gyroY.toInt())
            output.writeShort(sample.gyroZ.toInt())
            output.writeShort(sample.accelerationX.toInt())
            output.writeShort(sample.accelerationY.toInt())
            output.writeShort(sample.accelerationZ.toInt())
        }
        output.writeByte(frame.sweatLevel.ordinal)
        output.writeBoolean(frame.leadOff)
    }

    private sealed interface RecorderCommand {
        data class Start(val targetPath: String, val completion: CompletableDeferred<Unit>) : RecorderCommand
        data class Append(val frame: RecorderFrame) : RecorderCommand
        data class Stop(val completion: CompletableDeferred<Unit>) : RecorderCommand
    }
}

fun interface FrameRecorderFactory {
    fun create(scope: CoroutineScope): FrameRecorder
}
