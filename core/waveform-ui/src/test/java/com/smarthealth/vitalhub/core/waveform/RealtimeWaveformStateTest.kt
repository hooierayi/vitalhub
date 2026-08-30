package com.smarthealth.vitalhub.core.waveform

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeWaveformStateTest {
    @Test
    fun overwritesOldestSamplesWithoutGrowing() {
        val state = RealtimeWaveformState(capacity = 4)

        state.append(intArrayOf(1, 2, 3))
        state.append(intArrayOf(4, 5, 6))
        state.advanceRendering(elapsedSeconds = 1f, sampleRateHz = 10)

        state.read {
            assertEquals(2L, firstOrdinal)
            assertEquals(6L, nextOrdinal)
            assertEquals(listOf(3, 4, 5, 6), (firstOrdinal until nextOrdinal).map(::sampleAt))
        }
    }

    @Test
    fun keepsSubsequentBatchesInOneContinuousSampleSequence() {
        val state = RealtimeWaveformState(capacity = 8)

        state.append(intArrayOf(1, 2))
        state.append(intArrayOf(3, 4))
        state.advanceRendering(elapsedSeconds = 1f, sampleRateHz = 10)

        state.read {
            assertEquals(0L, firstOrdinal)
            assertEquals(4L, nextOrdinal)
            assertEquals(listOf(1, 2, 3, 4), (firstOrdinal until nextOrdinal).map(::sampleAt))
        }
    }

    @Test
    fun movesViewportOriginWithoutChangingStoredSamples() {
        val state = RealtimeWaveformState(capacity = 8)

        state.append(intArrayOf(-6_600, -6_500, -6_400))
        state.advanceRendering(elapsedSeconds = 1f, sampleRateHz = 10)

        state.read {
            assertEquals(-6_500f, viewportCenterSample)
            assertEquals(200f, viewportRangeSamples)
            assertEquals(listOf(-6_600, -6_500, -6_400), (firstOrdinal until nextOrdinal).map(::sampleAt))
        }
    }

}
