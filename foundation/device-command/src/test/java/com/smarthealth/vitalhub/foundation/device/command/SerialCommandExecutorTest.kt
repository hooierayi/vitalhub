package com.smarthealth.vitalhub.foundation.device.command

import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.transport.DeviceTransport
import com.smarthealth.vitalhub.foundation.device.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerialCommandExecutorTest {
    @Test
    fun keepsSingleCommandInFlightAndSelectsQueuedHighPriorityFirst() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val transport = FakeTransport()
        val executor = SerialCommandExecutor(transport, RecorderCommandEncoder(), scope)
        val active = async(dispatcher) { executor.execute(DeviceCommand.StartCollection) }
        runCurrent()
        val queuedNormal = async(dispatcher) { executor.execute(DeviceCommand.StartCollection) }
        val queuedHigh = async(dispatcher) { executor.execute(DeviceCommand.StopCollection) }
        runCurrent()
        assertEquals(listOf(0x01), transport.commandCodes())

        executor.accept(0xF1, 0)
        runCurrent()
        assertEquals(listOf(0x01, 0x02), transport.commandCodes())

        executor.accept(0xF2, 0)
        runCurrent()
        assertEquals(listOf(0x01, 0x02, 0x01), transport.commandCodes())
        executor.accept(0xF1, 0)
        runCurrent()

        assertTrue(active.await() is CommandResult.Success)
        assertTrue(queuedHigh.await() is CommandResult.Success)
        assertTrue(queuedNormal.await() is CommandResult.Success)
        scope.cancel()
    }

    private class FakeTransport : DeviceTransport {
        private val mutableState = MutableStateFlow(TransportState.CONNECTED)
        val writes = mutableListOf<ByteArray>()
        override val state: StateFlow<TransportState> = mutableState
        override val incomingBytes: Flow<ByteArray> = emptyFlow()
        override suspend fun connect(address: String) = Unit
        override suspend fun write(bytes: ByteArray) { writes += bytes }
        override suspend fun disconnect() = Unit
        fun commandCodes(): List<Int> = writes.map { it[2].toInt() and 0xFF }
    }
}
