package com.smarthealth.vitalhub.foundation.device.waveform

import com.smarthealth.vitalhub.foundation.device.api.FrameContinuity
import com.smarthealth.vitalhub.foundation.device.api.FrameMetadata
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.SweatLevel
import com.smarthealth.vitalhub.foundation.device.api.TemperatureBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformPipelineTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun projectsAggregateFrameIntoIndependentEcgAndRespirationFlows() = runTest {
        val pipeline = DefaultWaveformPipeline()
        val ecg = intArrayOf(-32_768, 0, 32_767)
        val respiration = intArrayOf(-8_388_608, 0, 8_388_607)
        val ecgResult = async { pipeline.ecgFrames.first() }
        val respirationResult = async { pipeline.respirationFrames.first() }
        runCurrent()

        pipeline.accept(
            RecorderFrame(
                metadata = FrameMetadata(
                    sequence = 9,
                    receivedAtMillis = 123L,
                    continuity = FrameContinuity.GAP,
                    protocolVersion = 1,
                ),
                ecg = ecg,
                respiration = respiration,
                temperature = TemperatureBlock(0.0, 0.0, 0.0),
                motion = emptyList(),
                sweatLevel = SweatLevel.UNKNOWN,
                leadOff = false,
            ),
        )

        with(ecgResult.await()) {
            assertEquals(9, sequence)
            assertEquals(FrameContinuity.GAP, continuity)
            assertArrayEquals(ecg, samples)
        }
        with(respirationResult.await()) {
            assertEquals(9, sequence)
            assertEquals(FrameContinuity.GAP, continuity)
            assertArrayEquals(respiration, samples)
        }
    }
}
