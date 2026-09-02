package com.smarthealth.vitalhub.foundation.file.protocol

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.io.DicomInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultContinuousDicomWriterTest {
    @Test
    fun writesReadablePart10WithConfirmedSignalLayout() {
        runBlocking {
            val root = Files.createTempDirectory("vitalhub-dicom-test").toFile()
            try {
            val output = root.resolve("output")
            val definition = definition()
            val writer = DefaultContinuousDicomWriterFactory().create(
                definition = definition,
                workspace = DicomWorkspace(root.resolve("work")),
                publisher = LocalFileDicomPublisher(output, "sample"),
            )
            writer.append(frame())

            val result = writer.finish()

            assertEquals(1, result.segments.size)
            val file = File(result.segments.single().publishedFile.location)
            assertTrue(file.isFile)
            DicomInputStream(file).use { input ->
                val dataset = input.readDataset()
                assertEquals(UID.RawDataStorage, dataset.getString(Tag.SOPClassUID))
                assertEquals(
                    PrivateSchemaDefinition.VITALHUB_DATA_V1.creatorVersionUid,
                    dataset.getString(Tag.CreatorVersionUID),
                )
                assertEquals("1.2.3.4", dataset.getString(Tag.StudyInstanceUID))
                assertEquals("1.2.3.5", dataset.getString(Tag.SeriesInstanceUID))
                val waveforms = dataset.getSequence(Tag.WaveformSequence)
                assertEquals(2, waveforms.size)
                assertEquals(250, waveforms[0].getInt(Tag.NumberOfWaveformSamples, -1))
                assertEquals(16, waveforms[0].getInt(Tag.WaveformBitsAllocated, -1))
                assertEquals("SS", waveforms[0].getString(Tag.WaveformSampleInterpretation))
                val ecg = ByteBuffer.wrap(waveforms[0].getBytes(Tag.WaveformData))
                    .order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(-125, ecg.short.toInt())
                assertEquals(250, waveforms[1].getInt(Tag.NumberOfWaveformSamples, -1))
                assertEquals(32, waveforms[1].getInt(Tag.WaveformBitsAllocated, -1))
                assertEquals("SL", waveforms[1].getString(Tag.WaveformSampleInterpretation))
                val respiration = ByteBuffer.wrap(waveforms[1].getBytes(Tag.WaveformData))
                    .order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(-1_250, respiration.int)
                assertEquals("032Y", dataset.getString(Tag.PatientAge))
                assertEquals("VITALHUB_DATA_V1", dataset.getString(0x00110010))
                assertEquals("VitalHub Android/1.0", dataset.getString(Tag.SoftwareVersions))
                assertEquals("raw", dataset.getString(0x00111008))
                val ecgChannel = waveforms[0].getSequence(Tag.ChannelDefinitionSequence)[0]
                val ecgSource = ecgChannel.getSequence(Tag.ChannelSourceSequence)[0]
                assertEquals("2:0", ecgSource.getString(Tag.CodeValue))
                assertEquals("MDC", ecgSource.getString(Tag.CodingSchemeDesignator))
                assertEquals("Unspecified lead", ecgSource.getString(Tag.CodeMeaning))
                assertEquals("OK", ecgChannel.getString(Tag.ChannelStatus))
                assertEquals(400.0 / 32_767.0, ecgChannel.getDouble(Tag.ChannelSensitivity, 0.0), 1e-12)
                assertEquals(1.0, ecgChannel.getDouble(Tag.ChannelSensitivityCorrectionFactor, 0.0), 0.0)
                assertEquals(0.0, ecgChannel.getDouble(Tag.ChannelBaseline, 1.0), 0.0)
                assertEquals(-32_768, ecgChannel.getInt(Tag.ChannelMinimumValue, 0))
                assertEquals(32_767, ecgChannel.getInt(Tag.ChannelMaximumValue, 0))
                assertTrue(ecgChannel.contains(Tag.FilterLowFrequency))
                assertTrue(ecgChannel.contains(Tag.FilterHighFrequency))
                assertTrue(ecgChannel.contains(Tag.NotchFilterFrequency))
                assertTrue(ecgChannel.contains(Tag.NotchFilterBandwidth))
                assertEquals(null, ecgChannel.getString(Tag.FilterLowFrequency))
                val ecgUnit = ecgChannel.getSequence(Tag.ChannelSensitivityUnitsSequence)[0]
                assertEquals("mV", ecgUnit.getString(Tag.CodeValue))
                assertEquals("UCUM", ecgUnit.getString(Tag.CodingSchemeDesignator))

                val respirationChannel = waveforms[1].getSequence(Tag.ChannelDefinitionSequence)[0]
                assertEquals(
                    200.0 / 8_388_607.0,
                    respirationChannel.getDouble(Tag.ChannelSensitivity, 0.0),
                    1e-12,
                )
                assertEquals(-8_388_608, respirationChannel.getInt(Tag.ChannelMinimumValue, 0))
                assertEquals(8_388_607, respirationChannel.getInt(Tag.ChannelMaximumValue, 0))

                assertEquals(25.0, dataset.getDouble(Tag.WaveformDataDisplayScale, 0.0), 0.0)
                val presentation = dataset.getSequence(Tag.WaveformPresentationGroupSequence)[0]
                val display = presentation.getSequence(Tag.ChannelDisplaySequence)[0]
                assertTrue(display.getInts(Tag.ReferencedWaveformChannels).contentEquals(intArrayOf(1, 1)))
                assertEquals(0.5, display.getDouble(Tag.ChannelPosition, 0.0), 0.0)
                assertEquals(
                    (400.0 / 32_767.0) * 10.0,
                    display.getDouble(Tag.AbsoluteChannelDisplayScale, 0.0),
                    1e-7,
                )
                assertEquals("SS", dataset.getString(0x00111044))
                assertEquals(-999, ByteBuffer.wrap(dataset.getBytes(0x00111047))
                    .order(ByteOrder.LITTLE_ENDIAN).short.toInt())
                assertEquals("SS", dataset.getString(0x00111054))
                assertEquals("SS", dataset.getString(0x00111064))
                assertEquals(1, dataset.getInt(0x00111072, -1))
                assertEquals(0, dataset.getBytes(0x00111076).first().toInt())
            }
            } finally {
                root.deleteRecursively()
            }
        }
    }

    private fun definition() = DicomRecordingDefinition(
        sessionId = "session-1",
        patient = PatientDefinition(null, "patient-1", null, null, null, ageYears = 32),
        study = StudyDefinition("1.2.3.4", "study-1", 1_700_000_000_000L, null),
        series = SeriesDefinition("1.2.3.5", 1),
        equipment = EquipmentDefinition("", "Recorder", null, listOf("VitalHub Android/1.0")),
        acquisition = AcquisitionDefinition(1_700_000_000_000L, "+0800"),
        signalLayout = WearableSignalLayout(),
        writerPolicy = DicomWriterPolicy(rollover = DicomRolloverPolicy()),
    )

    private fun frame() = AcquiredWearableFrame(
        ecg = IntArray(250) { it - 125 },
        respiration = IntArray(250) { it * 10 - 1_250 },
        motion = List(5) { index ->
            WearableMotionSample(index, index + 1, index + 2, index + 3, index + 4, index + 5)
        },
        skinTemperatureRaw = -999,
        ambientTemperatureRaw = 2_520,
        ambientHumidityRaw = 5_640,
        sweatLevel = SweatLevelValue.UNKNOWN,
    )
}
