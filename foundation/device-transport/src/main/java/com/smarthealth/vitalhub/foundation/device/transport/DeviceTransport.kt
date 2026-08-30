package com.smarthealth.vitalhub.foundation.device.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class TransportState { DISCONNECTED, CONNECTING, CONNECTED }

interface DeviceTransport {
    val state: StateFlow<TransportState>
    val incomingBytes: Flow<ByteArray>

    suspend fun connect(address: String)
    suspend fun write(bytes: ByteArray)
    suspend fun disconnect()
}

fun interface DeviceTransportFactory {
    fun create(): DeviceTransport
}
