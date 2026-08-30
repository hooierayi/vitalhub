package com.smarthealth.vitalhub.foundation.device.waveform.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaveformDebugRegistryTest {
    @Before
    fun setUp() {
        WaveformDebugRegistry.setEnabled(true)
        WaveformDebugRegistry.clear()
    }

    @After
    fun tearDown() {
        WaveformDebugRegistry.setEnabled(false)
    }

    @Test
    fun reportsRingBufferCapacityOverwriteAndPendingSamples() {
        val state = RealtimeWaveformState(capacity = 4, debugLabel = "ECG")

        state.append(intArrayOf(1, 2, 3, 4, 5))
        val snapshot = WaveformDebugRegistry.snapshot().single()

        assertEquals("ECG", snapshot.label)
        assertEquals(4, snapshot.storedSamples)
        assertEquals(5L, snapshot.totalSamples)
        assertEquals(1L, snapshot.firstOrdinal)
        assertEquals(1L, snapshot.overwrittenSamples)
        assertEquals(4L, snapshot.pendingSamples)
        assertTrue(intArrayOf(1, 2, 3, 4, 5).contentEquals(snapshot.latestPreview))
    }

    @Test
    fun previewsHeadAndTailOfLargeAppend() {
        val state = RealtimeWaveformState(capacity = 32, debugLabel = "ECG")

        state.append(IntArray(24) { it + 1 })
        val preview = WaveformDebugRegistry.snapshot().single().latestPreview

        assertTrue(
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 17, 18, 19, 20, 21, 22, 23, 24)
                .contentEquals(preview),
        )
    }
}
