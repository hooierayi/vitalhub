package com.smarthealth.vitalhub.foundation.device.command

import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import com.smarthealth.vitalhub.foundation.device.transport.DeviceTransport
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

interface DeviceCommandExecutor {
    suspend fun execute(command: DeviceCommand): CommandResult
    fun accept(code: Int, status: Int)
    suspend fun cancelAll(cause: Throwable = CancellationException("Command executor stopped"))
}

class SerialCommandExecutor(
    private val transport: DeviceTransport,
    private val encoder: DeviceCommandEncoder,
    scope: CoroutineScope,
    queueCapacity: Int = 32,
    private val trace: DeviceTrace = DeviceTrace.NONE,
) : DeviceCommandExecutor {
    private val queue = PriorityCommandQueue(queueCapacity)
    private val receipts = Channel<CommandReceipt>(Channel.BUFFERED)
    private val executionScope = scope
    private val worker: Job = scope.launch { runWorker() }
    private var active: CommandRequest? = null
    private var activeExecution: Deferred<CommandResult>? = null

    override suspend fun execute(command: DeviceCommand): CommandResult {
        val request = CommandRequest(command)
        queue.put(request)
        trace.log("COMMAND", "queued=${command.name()}")
        return request.result.await()
    }

    override fun accept(code: Int, status: Int) {
        trace.log("COMMAND", "receipt code=0x${code.toString(16).uppercase()}, status=$status")
        receipts.trySend(CommandReceipt(code, status))
    }

    override suspend fun cancelAll(cause: Throwable) {
        activeExecution?.cancel(CancellationException(cause.message))
        active?.result?.complete(CommandResult.Failed(cause))
        active = null
        queue.drain().forEach { it.result.complete(CommandResult.Failed(cause)) }
        while (receipts.tryReceive().isSuccess) Unit
    }

    private suspend fun runWorker() {
        while (currentCoroutineContext().isActive) {
            val request = queue.take()
            active = request
            val execution = executionScope.async { runCommand(request.command) }
            activeExecution = execution
            val result = try {
                execution.await()
            } catch (cancelled: CancellationException) {
                CommandResult.Failed(cancelled)
            }
            request.result.complete(result)
            activeExecution = null
            active = null
        }
    }

    private suspend fun runCommand(command: DeviceCommand): CommandResult {
        val spec = RecorderCommandRegistry.specFor(command)
        val attemptsAllowed = if (spec.idempotent) spec.maxAttempts else 1
        var lastError: Throwable? = null
        for (attempt in 1..attemptsAllowed) {
            try {
                while (receipts.tryReceive().isSuccess) Unit
                val encoded = encoder.encode(command)
                trace.log(
                    "COMMAND",
                    "send=${command.name()}, attempt=$attempt/$attemptsAllowed, code=0x${encoded[2].toUByte().toString(16).uppercase()}, bytes=${encoded.size}",
                )
                transport.write(encoded)
                val receipt = withTimeout(spec.timeoutMillis) {
                    var matched: CommandReceipt? = null
                    while (matched == null) {
                        val candidate = receipts.receive()
                        if (candidate.code == spec.responseCode) matched = candidate
                    }
                    matched
                }
                return if (receipt.status == 0) {
                    trace.log("COMMAND", "success=${command.name()}, attempt=$attempt")
                    CommandResult.Success
                } else {
                    trace.log("COMMAND", "rejected=${command.name()}, status=${receipt.status}, attempt=$attempt")
                    CommandResult.Rejected(receipt.status)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
                trace.log("COMMAND", "failure=${command.name()}, attempt=$attempt, error=${error.message}")
            }
        }
        return CommandResult.Failed(checkNotNull(lastError))
    }

    private data class CommandRequest(
        val command: DeviceCommand,
        val result: CompletableDeferred<CommandResult> = CompletableDeferred(),
    )

    private class PriorityCommandQueue(
        private val capacity: Int,
    ) {
        private val mutex = Mutex()
        private val signal = Channel<Unit>(Channel.CONFLATED)
        private val high = ArrayDeque<CommandRequest>()
        private val normal = ArrayDeque<CommandRequest>()

        init {
            require(capacity > 0)
        }

        suspend fun put(request: CommandRequest) {
            mutex.withLock {
                check(high.size + normal.size < capacity) { "Command queue is full" }
                when (RecorderCommandRegistry.specFor(request.command).priority) {
                    CommandPriority.HIGH -> high.addLast(request)
                    CommandPriority.NORMAL -> normal.addLast(request)
                }
            }
            signal.trySend(Unit)
        }

        suspend fun take(): CommandRequest {
            while (true) {
                mutex.withLock {
                    val item = high.pollFirst() ?: normal.pollFirst()
                    if (item != null) return item
                }
                signal.receive()
            }
        }

        suspend fun drain(): List<CommandRequest> = mutex.withLock {
            buildList {
                while (high.isNotEmpty()) add(high.removeFirst())
                while (normal.isNotEmpty()) add(normal.removeFirst())
            }
        }
    }

    private data class CommandReceipt(val code: Int, val status: Int)

    private fun DeviceCommand.name(): String = when (this) {
        DeviceCommand.StartCollection -> "StartCollection"
        DeviceCommand.StopCollection -> "StopCollection"
        is DeviceCommand.StartContinuous -> "StartContinuous"
    }
}

internal enum class CommandPriority { HIGH, NORMAL }

internal data class CommandSpec(
    val responseCode: Int,
    val priority: CommandPriority,
    val timeoutMillis: Long,
    val maxAttempts: Int,
    val idempotent: Boolean,
)

object RecorderCommandRegistry {
    val receiptCodes: Set<Int> = setOf(0xF1, 0xF2, 0xF3)

    internal fun specFor(command: DeviceCommand): CommandSpec = when (command) {
        DeviceCommand.StartCollection -> CommandSpec(
            responseCode = 0xF1,
            priority = CommandPriority.NORMAL,
            timeoutMillis = 3_000,
            maxAttempts = 1,
            idempotent = false,
        )
        DeviceCommand.StopCollection -> CommandSpec(
            responseCode = 0xF2,
            priority = CommandPriority.HIGH,
            timeoutMillis = 3_000,
            maxAttempts = 2,
            idempotent = true,
        )
        is DeviceCommand.StartContinuous -> CommandSpec(
            responseCode = 0xF3,
            priority = CommandPriority.NORMAL,
            timeoutMillis = 3_000,
            maxAttempts = 1,
            idempotent = false,
        )
    }
}
