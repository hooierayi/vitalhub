package com.smarthealth.vitalhub.feature.collection.recording

import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.SweatLevel
import com.smarthealth.vitalhub.foundation.file.protocol.AbortReason
import com.smarthealth.vitalhub.foundation.file.protocol.AcquiredWearableFrame
import com.smarthealth.vitalhub.foundation.file.protocol.AppendReceipt
import com.smarthealth.vitalhub.foundation.file.protocol.ContinuousDicomWriter
import com.smarthealth.vitalhub.foundation.file.protocol.ContinuousDicomWriterFactory
import com.smarthealth.vitalhub.foundation.file.protocol.DicomPublisher
import com.smarthealth.vitalhub.foundation.file.protocol.DicomRecordingDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.DicomRecordingResult
import com.smarthealth.vitalhub.foundation.file.protocol.DicomWorkspace
import com.smarthealth.vitalhub.foundation.file.protocol.SweatLevelValue
import com.smarthealth.vitalhub.foundation.file.protocol.WearableMotionSample
import kotlin.math.roundToInt

class CollectionDicomRecorder(
    private val writerFactory: ContinuousDicomWriterFactory,
) {
    private var writer: ContinuousDicomWriter? = null

    suspend fun start(
        definition: DicomRecordingDefinition,
        workspace: DicomWorkspace,
        publisher: DicomPublisher,
    ) {
        check(writer == null) { "A DICOM recording is already active" }
        writer = writerFactory.create(definition, workspace, publisher)
    }

    suspend fun append(frame: RecorderFrame): AppendReceipt =
        checkNotNull(writer) { "No DICOM recording is active" }.append(frame.toDicomFrame())

    suspend fun stop(): DicomRecordingResult? {
        val active = writer ?: return null
        return try {
            active.finish()
        } finally {
            writer = null
        }
    }

    suspend fun abort(retainWorkspace: Boolean = true) {
        writer?.abort(AbortReason.UNRECOVERABLE_ERROR, retainWorkspace)
        writer = null
    }
}

internal fun RecorderFrame.toDicomFrame(): AcquiredWearableFrame = AcquiredWearableFrame(
    ecg = ecg,
    respiration = respiration,
    motion = motion.map { sample ->
        WearableMotionSample(
            gyroX = sample.gyroX.toInt(),
            gyroY = sample.gyroY.toInt(),
            gyroZ = sample.gyroZ.toInt(),
            accelerationX = sample.accelerationX.toInt(),
            accelerationY = sample.accelerationY.toInt(),
            accelerationZ = sample.accelerationZ.toInt(),
        )
    },
    skinTemperatureRaw = (temperature.skinCelsius * 100).roundToInt(),
    ambientTemperatureRaw = (temperature.ambientCelsius * 100).roundToInt(),
    ambientHumidityRaw = (temperature.humidityPercent * 100).roundToInt(),
    sweatLevel = when (sweatLevel) {
        SweatLevel.UNKNOWN -> SweatLevelValue.UNKNOWN
        SweatLevel.NONE -> SweatLevelValue.NONE
        SweatLevel.LIGHT -> SweatLevelValue.LIGHT
        SweatLevel.MEDIUM -> SweatLevelValue.MEDIUM
        SweatLevel.HEAVY -> SweatLevelValue.HEAVY
    },
)
