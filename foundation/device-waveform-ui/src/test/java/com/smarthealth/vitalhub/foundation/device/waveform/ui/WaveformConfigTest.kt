package com.smarthealth.vitalhub.foundation.device.waveform.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformConfigTest {
    @Test
    fun standardPaperAt250HzUsesTenSamplesPerSmallGrid() {
        val config = WaveformPaperConfig(
            sampleRateHz = 250,
            paperSpeed = PaperSpeed.MM_25_PER_SECOND,
        )

        assertEquals(0.1f, config.sampleSpacingMillimeters, 0.0001f)
        assertEquals(10f, config.samplesPerSmallGrid, 0.0001f)
    }

    @Test
    fun signalCalibrationsMapSignedLimitsSymmetrically() {
        assertEquals(400f, SignalCalibrations.Ecg.toMillivolts(32_767), 0.001f)
        assertEquals(-400f, SignalCalibrations.Ecg.toMillivolts(-32_768), 0.001f)
        assertEquals(200f, SignalCalibrations.Respiration.toMillivolts(8_388_607), 0.001f)
        assertEquals(-200f, SignalCalibrations.Respiration.toMillivolts(-8_388_608), 0.001f)
    }

    @Test
    fun scaleLabelUsesConfiguredPaperSpeedAndGain() {
        assertEquals(
            "12.5 mm/s · 20 mm/mV",
            waveformScaleLabel(PaperSpeed.MM_12_5_PER_SECOND, PaperGain.MM_20_PER_MV),
        )
    }
}
