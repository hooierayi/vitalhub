package com.smarthealth.vitalhub.foundation.file.protocol

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.BulkData
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomOutputStream

class DefaultContinuousDicomWriterFactory(
    private val uidGenerator: DicomUidGenerator = UuidDicomUidGenerator(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContinuousDicomWriterFactory {
    override suspend fun create(
        definition: DicomRecordingDefinition,
        workspace: DicomWorkspace,
        publisher: DicomPublisher,
    ): ContinuousDicomWriter = withContext(dispatcher) {
        check(workspace.directory.mkdirs() || workspace.directory.isDirectory) {
            "Cannot create DICOM workspace: ${workspace.directory}"
        }
        DefaultContinuousDicomWriter(definition, workspace, publisher, uidGenerator, dispatcher)
    }

    override suspend fun inspect(workspace: DicomWorkspace): DicomWorkspaceStatus =
        withContext(dispatcher) {
            val files = workspace.directory.listFiles().orEmpty()
            if (files.isEmpty()) DicomWorkspaceStatus.Empty else DicomWorkspaceStatus.Corrupted(
                listOf(DicomIssue("RECOVERY_NOT_AVAILABLE", "Workspace recovery is not implemented yet")),
            )
        }

    override suspend fun resume(
        workspace: DicomWorkspace,
        publisher: DicomPublisher,
    ): ContinuousDicomWriter {
        throw DicomWriteException(
            DicomWriteError.WorkspaceCorrupted("Workspace recovery is not implemented yet"),
        )
    }
}

private class DefaultContinuousDicomWriter(
    private val definition: DicomRecordingDefinition,
    private val workspace: DicomWorkspace,
    private val publisher: DicomPublisher,
    private val uidGenerator: DicomUidGenerator,
    private val dispatcher: CoroutineDispatcher,
) : ContinuousDicomWriter {
    override val sessionId: String = definition.sessionId
    private val mutableState = MutableStateFlow<DicomWriterState>(DicomWriterState.Creating)
    override val state: StateFlow<DicomWriterState> = mutableState.asStateFlow()
    private val mutableCompletedSegments = MutableSharedFlow<DicomSegmentResult>(extraBufferCapacity = 1)
    override val completedSegments: Flow<DicomSegmentResult> = mutableCompletedSegments.asSharedFlow()

    private val mutex = Mutex()
    private val segments = mutableListOf<DicomSegmentResult>()
    private var totalFrameCount = 0L
    private var segmentNumber = 1
    private var segmentStartedAtEpochMillis = definition.acquisition.startedAtEpochMillis
    private var payload = SegmentPayload.open(workspace.directory, segmentNumber)
    private var completedResult: DicomRecordingResult? = null

    init {
        mutableState.value = DicomWriterState.Open(1, 0, 0)
    }

    override suspend fun append(frame: AcquiredWearableFrame): AppendReceipt = mutex.withLock {
        check(mutableState.value is DicomWriterState.Open) { "DICOM writer is not open" }
        withContext(dispatcher) { payload.append(frame) }
        totalFrameCount++
        val acceptedFrameCount = totalFrameCount
        mutableState.value = DicomWriterState.Open(
            segmentNumber = segmentNumber,
            committedFrameCount = totalFrameCount,
            durableFrameCount = (mutableState.value as? DicomWriterState.Open)?.durableFrameCount ?: 0,
        )

        val rollover = if (payload.estimatedDicomBytes >= definition.writerPolicy.rollover.maxEstimatedFileBytes) {
            val completed = finalizeCurrentSegment()
            segmentNumber++
            segmentStartedAtEpochMillis = definition.acquisition.startedAtEpochMillis + totalFrameCount * 1_000L
            payload = SegmentPayload.open(workspace.directory, segmentNumber)
            mutableState.value = DicomWriterState.Open(segmentNumber, totalFrameCount, totalFrameCount)
            RolloverResult(completed, segmentNumber)
        } else {
            null
        }
        AppendReceipt(
            segmentNumber = rollover?.completedSegment?.segmentNumber ?: segmentNumber,
            acceptedFrameCount = acceptedFrameCount,
            rollover = rollover,
        )
    }

    override suspend fun checkpoint(): DicomCheckpoint = mutex.withLock {
        check(mutableState.value is DicomWriterState.Open) { "DICOM writer is not open" }
        withContext(dispatcher) { payload.checkpoint() }
        val checkpoint = DicomCheckpoint(
            sessionId = sessionId,
            segmentNumber = segmentNumber,
            durableFrameCount = totalFrameCount,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        mutableState.value = DicomWriterState.Open(segmentNumber, totalFrameCount, totalFrameCount)
        checkpoint
    }

    override suspend fun finish(): DicomRecordingResult = mutex.withLock {
        completedResult?.let { return@withLock it }
        check(mutableState.value is DicomWriterState.Open) { "DICOM writer is not open" }
        if (payload.frameCount > 0) finalizeCurrentSegment() else withContext(dispatcher) { payload.discard() }
        DicomRecordingResult(
            sessionId = sessionId,
            segments = segments.toList(),
            totalFrameCount = totalFrameCount,
            startedAtEpochMillis = definition.acquisition.startedAtEpochMillis,
            finishedAtEpochMillis = System.currentTimeMillis(),
        ).also {
            completedResult = it
            mutableState.value = DicomWriterState.Completed(it)
        }
    }

    override suspend fun abort(reason: AbortReason, retainWorkspace: Boolean) = mutex.withLock {
        if (mutableState.value is DicomWriterState.Completed) return@withLock
        withContext(dispatcher) {
            payload.close()
            if (!retainWorkspace) workspace.directory.deleteRecursively()
        }
        mutableState.value = DicomWriterState.Aborted(retainWorkspace)
    }

    private suspend fun finalizeCurrentSegment(): DicomSegmentResult {
        mutableState.value = DicomWriterState.Finalizing(segmentNumber, FinalizePhase.FLUSHING)
        return try {
            withContext(dispatcher) { payload.checkpoint(); payload.close() }
            val sopInstanceUid = uidGenerator.newSopInstanceUid()
            val descriptor = DicomInstanceDescriptor(
                sessionId = sessionId,
                segmentNumber = segmentNumber,
                studyInstanceUid = definition.study.studyInstanceUid,
                seriesInstanceUid = definition.series.seriesInstanceUid,
                sopInstanceUid = sopInstanceUid,
                instanceNumber = segmentNumber,
            )
            mutableState.value = DicomWriterState.Finalizing(segmentNumber, FinalizePhase.BUILDING_DATASET)
            val target = publisher.createPart(descriptor)
            try {
                mutableState.value = DicomWriterState.Finalizing(segmentNumber, FinalizePhase.WRITING_PART10)
                val checksum = withContext(dispatcher) { writeDicom(target, descriptor, payload) }
                mutableState.value = DicomWriterState.Finalizing(segmentNumber, FinalizePhase.PUBLISHING)
                val published = publisher.commit(target)
                val result = DicomSegmentResult(
                    segmentNumber = segmentNumber,
                    studyInstanceUid = descriptor.studyInstanceUid,
                    seriesInstanceUid = descriptor.seriesInstanceUid,
                    sopInstanceUid = descriptor.sopInstanceUid,
                    instanceNumber = descriptor.instanceNumber,
                    frameCount = payload.frameCount,
                    ecgSampleCount = payload.frameCount * definition.signalLayout.ecg.samplesPerFrame,
                    respirationSampleCount = payload.frameCount * definition.signalLayout.respiration.samplesPerFrame,
                    motionSampleCount = payload.frameCount * definition.signalLayout.motion.samplesPerFrame,
                    acquisitionStartedAtEpochMillis = segmentStartedAtEpochMillis,
                    acquisitionEndedAtEpochMillis = segmentStartedAtEpochMillis + payload.frameCount * 1_000L,
                    publishedFile = published,
                    checksum = checksum,
                )
                segments += result
                mutableCompletedSegments.emit(result)
                withContext(dispatcher) { payload.directory.deleteRecursively() }
                result
            } catch (error: Throwable) {
                publisher.discardPart(target)
                throw error
            }
        } catch (error: Throwable) {
            val writeError = DicomWriteError.EncodingFailed(error)
            mutableState.value = DicomWriterState.Failed(writeError, false, null)
            throw DicomWriteException(writeError, error)
        }
    }

    private suspend fun writeDicom(
        target: DicomPartTarget,
        descriptor: DicomInstanceDescriptor,
        payload: SegmentPayload,
    ): FileChecksum {
        val dataset = buildDataset(descriptor, payload)
        val fmi = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian)
        val digest = MessageDigest.getInstance("SHA-256")
        DicomOutputStream(DigestOutputStream(target.openOutputStream(), digest), UID.ExplicitVRLittleEndian).use { output ->
            output.writeDataset(fmi, dataset)
        }
        return FileChecksum(
            algorithm = "SHA-256",
            value = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    private fun buildDataset(descriptor: DicomInstanceDescriptor, payload: SegmentPayload): Attributes {
        val attrs = Attributes()
        attrs.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192")
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.RawDataStorage)
        attrs.setString(Tag.SOPInstanceUID, VR.UI, descriptor.sopInstanceUid)
        attrs.setString(Tag.CreatorVersionUID, VR.UI, definition.privateSchema.creatorVersionUid)
        attrs.setString(Tag.StudyInstanceUID, VR.UI, descriptor.studyInstanceUid)
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, descriptor.seriesInstanceUid)
        attrs.setInt(Tag.SeriesNumber, VR.IS, definition.series.seriesNumber)
        attrs.setInt(Tag.InstanceNumber, VR.IS, descriptor.instanceNumber)
        attrs.setString(Tag.Modality, VR.CS, definition.series.modality)
        attrs.setString(Tag.SeriesDescription, VR.LO, definition.series.description)
        attrs.setString(Tag.PatientID, VR.LO, definition.patient.patientId)
        attrs.setString(Tag.IssuerOfPatientID, VR.LO, definition.patient.issuerOfPatientId.orEmpty())
        attrs.setString(
            Tag.PatientName,
            VR.PN,
            definition.patient.patientName
                ?.let { listOfNotNull(it.familyName, it.givenName).joinToString("^") }
                .orEmpty(),
        )
        attrs.setString(Tag.PatientBirthDate, VR.DA, definition.patient.birthDate.orEmpty())
        definition.patient.ageYears?.let { ageYears ->
            require(ageYears in 0..999) { "Patient age must be between 0 and 999 years" }
            attrs.setString(Tag.PatientAge, VR.AS, "%03dY".format(Locale.US, ageYears))
        }
        attrs.setString(
            Tag.PatientSex,
            VR.CS,
            definition.patient.sex?.takeUnless { it == PatientSex.UNKNOWN }?.dicomCode.orEmpty(),
        )
        attrs.setString(Tag.StudyID, VR.SH, definition.study.studyId.orEmpty())
        attrs.setString(Tag.AccessionNumber, VR.SH, definition.study.accessionNumber.orEmpty())
        attrs.setString(Tag.ReferringPhysicianName, VR.PN, "")
        attrs.setString(Tag.StudyDate, VR.DA, dicomDate(definition.study.studyDateTimeEpochMillis))
        attrs.setString(Tag.StudyTime, VR.TM, dicomTime(definition.study.studyDateTimeEpochMillis))
        attrs.setString(Tag.ContentDate, VR.DA, dicomDate(segmentStartedAtEpochMillis))
        attrs.setString(Tag.ContentTime, VR.TM, dicomTime(segmentStartedAtEpochMillis))
        attrs.setString(Tag.AcquisitionDateTime, VR.DT, dicomDateTime(segmentStartedAtEpochMillis))
        attrs.setString(Tag.TimezoneOffsetFromUTC, VR.SH, definition.acquisition.timezoneOffset)
        attrs.setString(Tag.Manufacturer, VR.LO, definition.equipment.manufacturer)
        attrs.setString(Tag.ManufacturerModelName, VR.LO, definition.equipment.modelName)
        definition.equipment.serialNumber?.let { attrs.setString(Tag.DeviceSerialNumber, VR.LO, it) }
        if (definition.equipment.softwareVersions.isNotEmpty()) {
            attrs.setString(Tag.SoftwareVersions, VR.LO, *definition.equipment.softwareVersions.toTypedArray())
        }
        attrs.setInt(Tag.AcquisitionNumber, VR.IS, descriptor.segmentNumber)
        attrs.setString(Tag.ContentLabel, VR.CS, "WEARABLE_AGG")
        attrs.setString(Tag.ContentDescription, VR.LO, "VitalHub wearable aggregate data")
        attrs.newSequence(Tag.AcquisitionContextSequence, 0)
        when (val synchronization = definition.acquisition.timeSynchronization) {
            TimeSynchronization.NotSynchronized ->
                attrs.setString(Tag.AcquisitionTimeSynchronized, VR.CS, "N")
            is TimeSynchronization.Synchronized -> {
                attrs.setString(Tag.AcquisitionTimeSynchronized, VR.CS, "Y")
                attrs.setString(Tag.TimeSource, VR.SH, synchronization.timeSource)
                attrs.setString(
                    Tag.SynchronizationFrameOfReferenceUID,
                    VR.UI,
                    synchronization.synchronizationFrameOfReferenceUid,
                )
            }
        }
        addWaveforms(attrs, payload)
        addPrivateData(attrs, payload)
        return attrs
    }

    private fun addWaveforms(attrs: Attributes, payload: SegmentPayload) {
        val ecgPresentation = definition.signalLayout.ecg.presentation
        attrs.setDouble(
            Tag.WaveformDataDisplayScale,
            VR.FL,
            ecgPresentation.horizontalMillimetersPerSecond,
        )
        val sequence = attrs.newSequence(Tag.WaveformSequence, 2)
        sequence.add(waveformItem(
            label = definition.signalLayout.ecg.channelLabel,
            samples = payload.frameCount * definition.signalLayout.ecg.samplesPerFrame,
            frequency = definition.signalLayout.ecg.samplingFrequencyHz,
            bitsAllocated = definition.signalLayout.ecg.bitsAllocated,
            bitsStored = definition.signalLayout.ecg.bitsStored,
            interpretation = definition.signalLayout.ecg.sampleInterpretation.name,
            source = definition.signalLayout.ecg.channelSource,
            calibration = definition.signalLayout.ecg.calibration,
            minimumSampleValue = definition.signalLayout.ecg.minimumSampleValue,
            maximumSampleValue = definition.signalLayout.ecg.maximumSampleValue,
            file = payload.ecg.file,
        ))
        sequence.add(waveformItem(
            label = definition.signalLayout.respiration.channelLabel,
            samples = payload.frameCount * definition.signalLayout.respiration.samplesPerFrame,
            frequency = definition.signalLayout.respiration.samplingFrequencyHz,
            bitsAllocated = definition.signalLayout.respiration.dicomBitsAllocated,
            bitsStored = definition.signalLayout.respiration.dicomBitsStored,
            interpretation = definition.signalLayout.respiration.sampleInterpretation.name,
            source = definition.signalLayout.respiration.channelSource,
            calibration = definition.signalLayout.respiration.calibration,
            minimumSampleValue = definition.signalLayout.respiration.minimumSampleValue,
            maximumSampleValue = definition.signalLayout.respiration.maximumSampleValue,
            file = payload.respiration.file,
        ))
        addEcgPresentation(attrs, ecgPresentation, definition.signalLayout.ecg.calibration)
    }

    private fun waveformItem(
        label: String,
        samples: Long,
        frequency: Double,
        bitsAllocated: Int,
        bitsStored: Int,
        interpretation: String,
        source: DicomCode?,
        calibration: WaveformCalibration,
        minimumSampleValue: Int,
        maximumSampleValue: Int,
        file: File,
    ): Attributes = Attributes().apply {
        setDouble(Tag.MultiplexGroupTimeOffset, VR.DS, 0.0)
        setString(Tag.WaveformOriginality, VR.CS, "ORIGINAL")
        setInt(Tag.NumberOfWaveformChannels, VR.US, 1)
        setLong(Tag.NumberOfWaveformSamples, VR.UL, samples)
        setDouble(Tag.SamplingFrequency, VR.DS, frequency)
        setString(Tag.MultiplexGroupLabel, VR.SH, label)
        val channel = newSequence(Tag.ChannelDefinitionSequence, 1)
        channel.add(Attributes().apply {
            setInt(Tag.WaveformChannelNumber, VR.IS, 1)
            setString(Tag.ChannelLabel, VR.SH, label)
            setString(Tag.ChannelStatus, VR.CS, "OK")
            source?.let { code ->
                newSequence(Tag.ChannelSourceSequence, 1).add(codeAttributes(code))
            }
            setDouble(Tag.ChannelSensitivity, VR.DS, calibration.sensitivity)
            newSequence(Tag.ChannelSensitivityUnitsSequence, 1).add(codeAttributes(calibration.unit))
            setDouble(Tag.ChannelSensitivityCorrectionFactor, VR.DS, calibration.correctionFactor)
            setDouble(Tag.ChannelBaseline, VR.DS, calibration.baseline)
            setNull(Tag.FilterLowFrequency, VR.DS)
            setNull(Tag.FilterHighFrequency, VR.DS)
            setNull(Tag.NotchFilterFrequency, VR.DS)
            setNull(Tag.NotchFilterBandwidth, VR.DS)
            setDouble(Tag.ChannelTimeSkew, VR.DS, 0.0)
            setInt(Tag.WaveformBitsStored, VR.US, bitsStored)
            val sampleVr = if (interpretation == DicomSampleInterpretation.SS.name) VR.SS else VR.SL
            setInt(Tag.ChannelMinimumValue, sampleVr, minimumSampleValue)
            setInt(Tag.ChannelMaximumValue, sampleVr, maximumSampleValue)
        })
        setInt(Tag.WaveformBitsAllocated, VR.US, bitsAllocated)
        setString(Tag.WaveformSampleInterpretation, VR.CS, interpretation)
        setValue(Tag.WaveformData, VR.OW, bulk(file))
    }

    private fun addEcgPresentation(
        attrs: Attributes,
        presentation: WaveformPresentation,
        calibration: WaveformCalibration,
    ) {
        val groups = attrs.newSequence(Tag.WaveformPresentationGroupSequence, 1)
        groups.add(Attributes().apply {
            setInt(Tag.PresentationGroupNumber, VR.US, 1)
            val channels = newSequence(Tag.ChannelDisplaySequence, 1)
            channels.add(Attributes().apply {
                setInt(Tag.ReferencedWaveformChannels, VR.US, 1, 1)
                setInt(
                    Tag.ChannelRecommendedDisplayCIELabValue,
                    VR.US,
                    *presentation.recommendedDisplayCielab.toIntArray(),
                )
                setDouble(Tag.ChannelPosition, VR.FL, presentation.channelPosition)
                setDouble(
                    Tag.AbsoluteChannelDisplayScale,
                    VR.FL,
                    calibration.sensitivity * calibration.correctionFactor *
                        presentation.verticalMillimetersPerPhysicalUnit,
                )
            })
        })
    }

    private fun codeAttributes(code: DicomCode): Attributes = Attributes().apply {
        setString(Tag.CodeValue, VR.SH, code.value)
        setString(Tag.CodingSchemeDesignator, VR.SH, code.scheme)
        setString(Tag.CodeMeaning, VR.LO, code.meaning)
    }

    private fun addPrivateData(attrs: Attributes, payload: SegmentPayload) {
        attrs.setString(0x00110010, VR.LO, definition.privateSchema.creator)
        attrs.setString(0x00111001, VR.LO, "MOTION_6_AXIS")
        attrs.setInt(0x00111002, VR.US, 6)
        attrs.setLong(0x00111003, VR.UL, payload.frameCount * 5)
        attrs.setDouble(0x00111004, VR.DS, 5.0)
        attrs.setInt(0x00111005, VR.US, 16)
        attrs.setString(0x00111006, VR.CS, "SS")
        attrs.setString(0x00111007, VR.LO, "GYRO_X,GYRO_Y,GYRO_Z,ACC_X,ACC_Y,ACC_Z")
        attrs.setString(0x00111008, VR.LO, definition.signalLayout.motion.unit)
        attrs.setValue(0x00111009, VR.OW, bulk(payload.motion.file))
        addMetric(
            attrs, 0x1040, "SKIN_TEMP", definition.signalLayout.skinTemperature,
            payload.skinTemperature.file, payload.frameCount,
        )
        addMetric(
            attrs, 0x1050, "AMBIENT_TEMP", definition.signalLayout.ambientTemperature,
            payload.ambientTemperature.file, payload.frameCount,
        )
        addMetric(
            attrs, 0x1060, "AMBIENT_RH", definition.signalLayout.humidity,
            payload.humidity.file, payload.frameCount,
        )
        attrs.setString(0x00111070, VR.LO, "SWEAT_LEVEL")
        attrs.setDouble(0x00111071, VR.DS, 1.0)
        attrs.setLong(0x00111072, VR.UL, payload.frameCount)
        attrs.setInt(0x00111073, VR.US, 8)
        attrs.setString(0x00111074, VR.CS, "UB")
        attrs.setString(0x00111075, VR.LT, "00=Unknown;01=NoSweat;02=Light;03=Medium;04=Heavy")
        attrs.setValue(0x00111076, VR.OB, bulk(payload.sweat.file))
        attrs.setInt(0x00111080, VR.US, 24)
        attrs.setString(0x00111081, VR.CS, "BIG_ENDIAN")
    }

    private fun addMetric(
        attrs: Attributes,
        element: Int,
        type: String,
        layout: MetricLayout,
        file: File,
        count: Long,
    ) {
        fun tag(offset: Int) = 0x00110000 or (element + offset)
        attrs.setString(tag(0), VR.LO, type)
        attrs.setDouble(tag(1), VR.DS, layout.samplingFrequencyHz)
        attrs.setLong(tag(2), VR.UL, count)
        attrs.setInt(tag(3), VR.US, layout.bitsAllocated)
        attrs.setString(tag(4), VR.CS, layout.sampleInterpretation.name)
        attrs.setString(tag(5), VR.LO, layout.unit)
        attrs.setDouble(tag(6), VR.DS, layout.scale)
        attrs.setValue(tag(7), VR.OW, bulk(file))
    }

    private fun bulk(file: File): BulkData = BulkData(file.toURI().toString(), 0, file.length(), false)

    private fun dicomDate(epochMillis: Long): String = format(epochMillis, "yyyyMMdd")
    private fun dicomTime(epochMillis: Long): String = format(epochMillis, "HHmmss.SSS")
    private fun dicomDateTime(epochMillis: Long): String =
        format(epochMillis, "yyyyMMddHHmmss.SSS") + definition.acquisition.timezoneOffset

    private fun format(epochMillis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = timezone(definition.acquisition.timezoneOffset)
        }.format(Date(epochMillis))

    private fun timezone(offset: String): TimeZone {
        val normalized = if (offset.length == 5) "GMT${offset.substring(0, 3)}:${offset.substring(3)}" else "GMT"
        return TimeZone.getTimeZone(normalized)
    }

}

private class SegmentPayload private constructor(
    val directory: File,
    val ecg: PayloadFile,
    val respiration: PayloadFile,
    val motion: PayloadFile,
    val skinTemperature: PayloadFile,
    val ambientTemperature: PayloadFile,
    val humidity: PayloadFile,
    val sweat: PayloadFile,
) {
    var frameCount: Long = 0
        private set

    val estimatedDicomBytes: Long
        get() = 64 * 1024L + listOf(ecg, respiration, motion, skinTemperature, ambientTemperature, humidity, sweat)
            .sumOf(PayloadFile::bytesWritten)

    fun append(frame: AcquiredWearableFrame) {
        frame.ecg.forEach(ecg::writeInt16LittleEndian)
        frame.respiration.forEach(respiration::writeInt32LittleEndian)
        frame.motion.forEach { sample ->
            motion.writeInt16LittleEndian(sample.gyroX)
            motion.writeInt16LittleEndian(sample.gyroY)
            motion.writeInt16LittleEndian(sample.gyroZ)
            motion.writeInt16LittleEndian(sample.accelerationX)
            motion.writeInt16LittleEndian(sample.accelerationY)
            motion.writeInt16LittleEndian(sample.accelerationZ)
        }
        skinTemperature.writeInt16LittleEndian(frame.skinTemperatureRaw)
        ambientTemperature.writeInt16LittleEndian(frame.ambientTemperatureRaw)
        humidity.writeInt16LittleEndian(frame.ambientHumidityRaw)
        sweat.writeByte(frame.sweatLevel.encodedValue)
        frameCount++
    }

    fun checkpoint() = files().forEach(PayloadFile::checkpoint)
    fun close() = files().forEach(PayloadFile::close)
    fun discard() { close(); directory.deleteRecursively() }
    private fun files() = listOf(ecg, respiration, motion, skinTemperature, ambientTemperature, humidity, sweat)

    companion object {
        fun open(workspace: File, segmentNumber: Int): SegmentPayload {
            val directory = File(workspace, "segment-${segmentNumber.toString().padStart(3, '0')}")
            check(directory.mkdirs()) { "DICOM segment workspace already exists: $directory" }
            return SegmentPayload(
                directory,
                PayloadFile(File(directory, "ecg.s16le")),
                PayloadFile(File(directory, "resp.s32le")),
                PayloadFile(File(directory, "motion.s16le")),
                PayloadFile(File(directory, "skin-temp.s16le")),
                PayloadFile(File(directory, "ambient-temp.s16le")),
                PayloadFile(File(directory, "humidity.s16le")),
                PayloadFile(File(directory, "sweat.u8")),
            )
        }
    }
}

private class PayloadFile(val file: File) {
    private val fileOutput = FileOutputStream(file)
    private val output = BufferedOutputStream(fileOutput)
    var bytesWritten: Long = 0
        private set
    fun writeByte(value: Int) {
        output.write(value)
        bytesWritten++
    }
    fun writeInt16LittleEndian(value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        bytesWritten += 2
    }
    fun writeInt32LittleEndian(value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
        bytesWritten += 4
    }
    fun checkpoint() { output.flush(); fileOutput.fd.sync() }
    fun close() = output.close()
}
