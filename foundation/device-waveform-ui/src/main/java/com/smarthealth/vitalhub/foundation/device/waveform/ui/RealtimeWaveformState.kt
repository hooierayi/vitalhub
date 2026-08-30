package com.smarthealth.vitalhub.foundation.device.waveform.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.floor

@Stable
class RealtimeWaveformState internal constructor(
    val capacity: Int,
) {
    private val lock = Any()
    private val samples = IntArray(capacity)
    private val reader = Reader()
    private var totalSamples = 0L
    private var renderedNextOrdinal = 0L
    private var storedSamples = 0
    private var fractionalSamples = 0f
    private val recentMinimums = IntArray(VIEWPORT_RANGE_WINDOW_COUNT)
    private val recentMaximums = IntArray(VIEWPORT_RANGE_WINDOW_COUNT)
    private var rangeWindowCount = 0
    private var rangeWindowWriteIndex = 0
    private var samplesSinceViewportUpdate = 0
    private var displayCenterSample = 0f
    private var displayRangeSamples = 0f
    internal var revision by mutableStateOf(0)
        private set

    init {
        require(capacity > 1)
    }

    fun append(samples: IntArray) {
        if (samples.isEmpty()) return
        synchronized(lock) {
            updateViewport(samples)
            samples.forEach { sample ->
                val targetIndex = (totalSamples % capacity).toInt()
                this.samples[targetIndex] = sample
                totalSamples++
                if (storedSamples < capacity) storedSamples++
            }
            val firstAvailableOrdinal = totalSamples - storedSamples
            if (renderedNextOrdinal < firstAvailableOrdinal) {
                renderedNextOrdinal = firstAvailableOrdinal
            }
        }
    }

    internal fun advanceRendering(elapsedSeconds: Float, sampleRateHz: Int) {
        if (elapsedSeconds <= 0f || sampleRateHz <= 0) return
        var advanced = false
        synchronized(lock) {
            val requestedWithFraction = fractionalSamples + elapsedSeconds * sampleRateHz
            val requested = floor(requestedWithFraction).toLong()
            if (requested <= 0L) {
                fractionalSamples = requestedWithFraction
                return@synchronized
            }
            val available = totalSamples - renderedNextOrdinal
            val actual = minOf(requested, available)
            if (actual > 0L) {
                renderedNextOrdinal += actual
                advanced = true
            }
            fractionalSamples = if (actual == requested) {
                requestedWithFraction - requested
            } else {
                0f
            }
        }
        if (advanced) revision++
    }

    fun clear() {
        synchronized(lock) {
            totalSamples = 0L
            renderedNextOrdinal = 0L
            storedSamples = 0
            fractionalSamples = 0f
            rangeWindowCount = 0
            rangeWindowWriteIndex = 0
            samplesSinceViewportUpdate = 0
            displayCenterSample = 0f
            displayRangeSamples = 0f
        }
        revision++
    }

    internal fun <T> read(block: Reader.() -> T): T = synchronized(lock) {
        reader.block()
    }

    internal inner class Reader {
        val firstOrdinal: Long
            get() = maxOf(totalSamples - storedSamples, 0L)

        val nextOrdinal: Long
            get() = renderedNextOrdinal

        /** Raw samples stay untouched; this value only translates the drawing coordinate origin. */
        val viewportCenterSample: Float
            get() = displayCenterSample

        val viewportRangeSamples: Float
            get() = displayRangeSamples

        fun contains(ordinal: Long): Boolean = ordinal in firstOrdinal until nextOrdinal

        fun sampleAt(ordinal: Long): Int {
            check(contains(ordinal)) { "Sample $ordinal is outside [$firstOrdinal, $nextOrdinal)" }
            return samples[(ordinal % capacity).toInt()]
        }
    }

    private fun updateViewport(newSamples: IntArray) {
        var minimum = newSamples[0]
        var maximum = newSamples[0]
        for (index in 1 until newSamples.size) {
            val sample = newSamples[index]
            if (sample < minimum) minimum = sample
            if (sample > maximum) maximum = sample
        }
        recentMinimums[rangeWindowWriteIndex] = minimum
        recentMaximums[rangeWindowWriteIndex] = maximum
        rangeWindowWriteIndex = (rangeWindowWriteIndex + 1) % VIEWPORT_RANGE_WINDOW_COUNT
        if (rangeWindowCount < VIEWPORT_RANGE_WINDOW_COUNT) rangeWindowCount++
        samplesSinceViewportUpdate += newSamples.size

        if (rangeWindowCount == 1 || samplesSinceViewportUpdate >= VIEWPORT_UPDATE_SAMPLE_COUNT) {
            recalculateViewport()
            samplesSinceViewportUpdate = 0
        }
    }

    private fun recalculateViewport() {
        val minimums = recentMinimums.copyOf(rangeWindowCount).apply { sort() }
        val maximums = recentMaximums.copyOf(rangeWindowCount).apply { sort() }
        val trimCount = when {
            rangeWindowCount > 6 -> 2
            rangeWindowCount > 2 -> 1
            else -> 0
        }
        val start = trimCount
        val endExclusive = rangeWindowCount - trimCount
        val robustMinimum = minimums.averageBetween(start, endExclusive)
        val robustMaximum = maximums.averageBetween(start, endExclusive)
        displayCenterSample = (robustMinimum + robustMaximum) / 2f
        displayRangeSamples = (robustMaximum - robustMinimum).coerceAtLeast(0f)
    }

    private fun IntArray.averageBetween(start: Int, endExclusive: Int): Float {
        var sum = 0L
        for (index in start until endExclusive) sum += this[index]
        return sum.toFloat() / (endExclusive - start)
    }

}

@Composable
fun rememberRealtimeWaveformState(
    sampleRateHz: Int = 250,
    historyDurationSeconds: Int = 30,
): RealtimeWaveformState {
    require(sampleRateHz > 0)
    require(historyDurationSeconds > 0)
    return remember(sampleRateHz, historyDurationSeconds) {
        RealtimeWaveformState(sampleRateHz * historyDurationSeconds)
    }
}

private const val VIEWPORT_RANGE_WINDOW_COUNT = 8
private const val VIEWPORT_UPDATE_SAMPLE_COUNT = 500
