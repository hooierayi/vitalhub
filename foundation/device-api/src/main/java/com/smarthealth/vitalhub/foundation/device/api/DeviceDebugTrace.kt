package com.smarthealth.vitalhub.foundation.device.api

import java.util.ArrayDeque

/** Inert unless a debug-only consumer enables it. Keeps DoKit out of the device SDK. */
object DeviceDebugTrace {
    private const val MAX_EVENTS = 500
    private const val MAX_RETAINED_GATT_EVENTS = 100
    private val lock = Any()
    private val events = ArrayDeque<DeviceDebugEvent>(MAX_EVENTS)
    private val retainedGattEvents = ArrayDeque<DeviceDebugEvent>(MAX_RETAINED_GATT_EVENTS)
    private val latestEventByStage = mutableMapOf<String, DeviceDebugEvent>()

    @Volatile
    private var enabled = false

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) clear()
    }

    fun record(stage: String, message: String, payload: ByteArray? = null) {
        if (!enabled) return
        val event = DeviceDebugEvent(
            timestampMillis = System.currentTimeMillis(),
            stage = stage,
            message = message,
            payload = payload?.copyOf(),
        )
        synchronized(lock) {
            while (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(event)
            latestEventByStage[stage] = event
            if (stage == GATT_STAGE) {
                while (retainedGattEvents.size >= MAX_RETAINED_GATT_EVENTS) retainedGattEvents.removeFirst()
                retainedGattEvents.addLast(event)
            }
        }
    }

    fun snapshot(stages: Set<String>): List<DeviceDebugEvent> = synchronized(lock) {
        val buffered = events.filter { it.stage in stages }
        val latest = stages.mapNotNull(latestEventByStage::get)
        val retainedGatt = if (GATT_STAGE in stages) retainedGattEvents else emptyList()
        (retainedGatt + buffered + latest).distinct().sortedBy { it.timestampMillis }
    }

    fun clear(stages: Set<String>? = null) = synchronized(lock) {
        if (stages == null) {
            events.clear()
            retainedGattEvents.clear()
            latestEventByStage.clear()
        } else {
            val retained = events.filterNot { it.stage in stages }
            events.clear()
            events.addAll(retained)
            if (GATT_STAGE in stages) retainedGattEvents.clear()
            stages.forEach(latestEventByStage::remove)
        }
    }

    private const val GATT_STAGE = "GATT"
}

data class DeviceDebugEvent(
    val timestampMillis: Long,
    val stage: String,
    val message: String,
    val payload: ByteArray?,
)
