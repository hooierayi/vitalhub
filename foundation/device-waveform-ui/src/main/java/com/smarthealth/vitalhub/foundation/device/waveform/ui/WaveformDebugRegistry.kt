package com.smarthealth.vitalhub.foundation.device.waveform.ui

import java.util.concurrent.ConcurrentHashMap

/** Debug snapshot sink. It remains disabled in release builds. */
object WaveformDebugRegistry {
    private val snapshots = ConcurrentHashMap<String, WaveformBufferDebugSnapshot>()

    @Volatile
    private var enabled = false

    internal val isEnabled: Boolean
        get() = enabled

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) snapshots.clear()
    }

    internal fun publish(snapshot: WaveformBufferDebugSnapshot) {
        if (enabled) snapshots[snapshot.label] = snapshot
    }

    fun snapshot(): List<WaveformBufferDebugSnapshot> =
        snapshots.values.sortedBy { it.label }

    fun clear() = snapshots.clear()
}

data class WaveformBufferDebugSnapshot(
    val label: String,
    val capacity: Int,
    val storedSamples: Int,
    val totalSamples: Long,
    val firstOrdinal: Long,
    val renderedNextOrdinal: Long,
    val pendingSamples: Long,
    val overwrittenSamples: Long,
    val viewportCenter: Float,
    val viewportRange: Float,
    val latestAppendCount: Int,
    val latestMinimum: Int,
    val latestMaximum: Int,
    val latestPreview: IntArray,
    val updatedAtMillis: Long,
)
