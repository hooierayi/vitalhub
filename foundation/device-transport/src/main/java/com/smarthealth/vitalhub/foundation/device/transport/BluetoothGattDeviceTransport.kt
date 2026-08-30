package com.smarthealth.vitalhub.foundation.device.transport

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattIndicateCallback
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattNotifyCallback
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import com.smarthealth.vitalhub.foundation.device.api.traceHex
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BluetoothDeviceProfile(
    val serviceUuid: UUID,
    val notifyCharacteristicUuid: UUID,
    val writeCharacteristicUuid: UUID,
    val useNotificationDescriptor: Boolean = false,
    val splitWrite: Boolean = true,
    val writeIntervalMillis: Long = 0,
    val channelType: BluetoothChannelType = BluetoothChannelType.NOTIFY,
)

enum class BluetoothChannelType { NOTIFY, INDICATE }

fun interface BluetoothGattDeviceResolver {
    fun resolve(address: String): BluetoothGattDevice
}

fun interface BluetoothDeviceProfileResolver {
    fun resolve(device: BluetoothGattDevice): BluetoothDeviceProfile
}

class BluetoothGattDeviceTransport(
    private val profileResolver: BluetoothDeviceProfileResolver,
    private val resolver: BluetoothGattDeviceResolver,
    private val trace: DeviceTrace = DeviceTrace.NONE,
) : DeviceTransport {
    private val mutableState = MutableStateFlow(TransportState.DISCONNECTED)
    private val incomingChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var device: BluetoothGattDevice? = null
    private var activeProfile: BluetoothDeviceProfile? = null

    constructor(
        profile: BluetoothDeviceProfile,
        resolver: BluetoothGattDeviceResolver,
        trace: DeviceTrace = DeviceTrace.NONE,
    ) : this(BluetoothDeviceProfileResolver { profile }, resolver, trace)

    override val state = mutableState.asStateFlow()
    override val incomingBytes: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    override suspend fun connect(address: String) {
        if (device?.key != null && device?.key != address) disconnect()
        mutableState.value = TransportState.CONNECTING
        trace.log("GATT", "connect address=$address")
        val target = resolver.resolve(address)
        device = target
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            fun fail(error: Throwable) {
                mutableState.value = TransportState.DISCONNECTED
                trace.log("GATT", "failure=${error.message}")
                if (completed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
            fun completeConnected() {
                mutableState.value = TransportState.CONNECTED
                trace.log("GATT", "notification channel ready")
                if (completed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            fun emit(payload: ByteArray) {
                trace.log("BLE_RX", "bytes=${payload.size}, hex=${payload.traceHex()}")
                incomingChannel.trySend(payload.copyOf())
            }
            fun enableDataChannel() {
                val profile = runCatching { profileResolver.resolve(target) }
                    .getOrElse { error ->
                        fail(error)
                        return
                    }
                activeProfile = profile
                trace.log(
                    "GATT",
                    "profile service=${profile.serviceUuid}, receive=${profile.notifyCharacteristicUuid}, " +
                        "write=${profile.writeCharacteristicUuid}, type=${profile.channelType}, descriptorByCharacteristic=${profile.useNotificationDescriptor}",
                )
                when (profile.channelType) {
                    BluetoothChannelType.NOTIFY -> target.enableNotification(
                        profile.serviceUuid,
                        profile.notifyCharacteristicUuid,
                        profile.useNotificationDescriptor,
                        object : BluetoothGattNotifyCallback {
                            override fun onNotifySuccess() = completeConnected()
                            override fun onNotifyFailure(exception: Exception) = fail(exception)
                            override fun onCharacteristicChanged(payload: ByteArray) = emit(payload)
                        },
                    )
                    BluetoothChannelType.INDICATE -> target.enableIndication(
                        profile.serviceUuid,
                        profile.notifyCharacteristicUuid,
                        profile.useNotificationDescriptor,
                        object : BluetoothGattIndicateCallback {
                            override fun onIndicateSuccess() = completeConnected()
                            override fun onIndicateFailure(exception: Exception) = fail(exception)
                            override fun onCharacteristicChanged(payload: ByteArray) = emit(payload)
                        },
                    )
                }
            }
            target.connect(object : BluetoothConnectCallback {
                override fun onConnectStart() = Unit

                override fun onConnectFailure(device: BluetoothKitDevice, e: Exception) = fail(e)

                override fun onConnectSuccess(device: BluetoothKitDevice) {
                    trace.log("GATT", "connected, enabling data channel")
                    enableDataChannel()
                }

                override fun onDisConnected(device: BluetoothKitDevice, active: Boolean) {
                    mutableState.value = TransportState.DISCONNECTED
                    trace.log("GATT", "disconnected active=$active")
                    if (!active && !completed.get()) {
                        fail(IllegalStateException("Bluetooth device disconnected"))
                    }
                }
            })
            continuation.invokeOnCancellation { target.disconnect() }
        }
    }

    override suspend fun write(bytes: ByteArray) {
        val target = checkNotNull(device) { "Transport is not connected" }
        val profile = checkNotNull(activeProfile) { "Bluetooth profile is not resolved" }
        check(state.value == TransportState.CONNECTED) { "Transport is not connected" }
        trace.log("BLE_TX", "bytes=${bytes.size}, header=${bytes.traceHex(4)}")
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            target.write(
                profile.serviceUuid,
                profile.writeCharacteristicUuid,
                bytes,
                profile.splitWrite,
                true,
                profile.writeIntervalMillis,
                object : BluetoothWriteCallback {
                    override fun onWriteSuccess(index: Int, amount: Int, justWrite: ByteArray) {
                        trace.log("BLE_TX", "part=$index/$amount, bytes=${justWrite.size}, success=true")
                        if (index >= amount && completed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onWriteFailure(exception: Exception) {
                        trace.log("BLE_TX", "success=false, error=${exception.message}")
                        if (completed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
                },
            )
        }
    }

    override suspend fun disconnect() {
        trace.log("GATT", "disconnect requested")
        device?.disconnect()
        device = null
        activeProfile = null
        mutableState.value = TransportState.DISCONNECTED
    }
}
