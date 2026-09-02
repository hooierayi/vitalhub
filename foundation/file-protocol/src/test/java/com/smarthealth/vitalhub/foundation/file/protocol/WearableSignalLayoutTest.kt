package com.smarthealth.vitalhub.foundation.file.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class WearableSignalLayoutTest {
    private val layout = WearableSignalLayout()

    @Test
    fun usesConfirmedWaveformAndMotionFormats() {
        assertEquals(250.0, layout.ecg.samplingFrequencyHz, 0.0)
        assertEquals(250, layout.ecg.samplesPerFrame)
        assertEquals(16, layout.ecg.bitsAllocated)
        assertEquals(16, layout.ecg.bitsStored)
        assertEquals(DicomSampleInterpretation.SS, layout.ecg.sampleInterpretation)
        assertEquals("ECG", layout.ecg.channelLabel)
        assertEquals(-32_768, layout.ecg.minimumSampleValue)
        assertEquals(32_767, layout.ecg.maximumSampleValue)
        assertEquals(400.0 / 32_767.0, layout.ecg.calibration.sensitivity, 0.0)
        assertEquals("mV", layout.ecg.calibration.unit.value)
        assertEquals("UCUM", layout.ecg.calibration.unit.scheme)
        assertEquals(1.0, layout.ecg.calibration.correctionFactor, 0.0)
        assertEquals(0.0, layout.ecg.calibration.baseline, 0.0)
        assertEquals(25.0, layout.ecg.presentation.horizontalMillimetersPerSecond, 0.0)
        assertEquals(10.0, layout.ecg.presentation.verticalMillimetersPerPhysicalUnit, 0.0)
        assertEquals(0.5, layout.ecg.presentation.channelPosition, 0.0)

        assertEquals(250.0, layout.respiration.samplingFrequencyHz, 0.0)
        assertEquals(250, layout.respiration.samplesPerFrame)
        assertEquals(24, layout.respiration.sourceBitsAllocated)
        assertEquals(32, layout.respiration.dicomBitsAllocated)
        assertEquals(24, layout.respiration.dicomBitsStored)
        assertEquals(DicomSampleInterpretation.SL, layout.respiration.sampleInterpretation)
        assertEquals("RESP", layout.respiration.channelLabel)
        assertEquals(-8_388_608, layout.respiration.minimumSampleValue)
        assertEquals(8_388_607, layout.respiration.maximumSampleValue)
        assertEquals(200.0 / 8_388_607.0, layout.respiration.calibration.sensitivity, 0.0)
        assertEquals("mV", layout.respiration.calibration.unit.value)
        assertEquals("UCUM", layout.respiration.calibration.unit.scheme)
        assertEquals(1.0, layout.respiration.calibration.correctionFactor, 0.0)
        assertEquals(0.0, layout.respiration.calibration.baseline, 0.0)

        assertEquals(5.0, layout.motion.samplingFrequencyHz, 0.0)
        assertEquals(5, layout.motion.samplesPerFrame)
        assertEquals(16, layout.motion.bitsAllocated)
        assertEquals(16, layout.motion.bitsStored)
        assertEquals(DicomSampleInterpretation.SS, layout.motion.sampleInterpretation)
        assertEquals("raw", layout.motion.unit)

        assertEquals("2:0", layout.ecg.channelSource.value)
        assertEquals("MDC", layout.ecg.channelSource.scheme)
        assertEquals("Unspecified lead", layout.ecg.channelSource.meaning)
    }

    @Test
    fun usesConfirmedMetricFormatsAndSweatCodes() {
        listOf(layout.skinTemperature, layout.ambientTemperature).forEach { temperature ->
            assertEquals(1.0, temperature.samplingFrequencyHz, 0.0)
            assertEquals(16, temperature.bitsAllocated)
            assertEquals(16, temperature.bitsStored)
            assertEquals(DicomSampleInterpretation.SS, temperature.sampleInterpretation)
            assertEquals(0.01, temperature.scale, 0.0)
            assertEquals("degC", temperature.unit)
        }

        assertEquals(DicomSampleInterpretation.SS, layout.humidity.sampleInterpretation)
        assertEquals(0.01, layout.humidity.scale, 0.0)
        assertEquals("%", layout.humidity.unit)

        assertEquals(0, SweatLevelValue.UNKNOWN.encodedValue)
        assertEquals(1, SweatLevelValue.NONE.encodedValue)
        assertEquals(2, SweatLevelValue.LIGHT.encodedValue)
        assertEquals(3, SweatLevelValue.MEDIUM.encodedValue)
        assertEquals(4, SweatLevelValue.HEAVY.encodedValue)
    }
}
