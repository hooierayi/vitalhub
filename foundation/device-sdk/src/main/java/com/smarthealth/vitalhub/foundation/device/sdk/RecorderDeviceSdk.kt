package com.smarthealth.vitalhub.foundation.device.sdk

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit
import com.smarthealth.vitalhub.foundation.device.api.DeviceSdk
import com.smarthealth.vitalhub.foundation.device.api.DeviceDebugTrace
import com.smarthealth.vitalhub.foundation.device.api.DeviceTrace
import com.smarthealth.vitalhub.foundation.device.command.RecorderCommandEncoder
import com.smarthealth.vitalhub.foundation.device.command.RecorderCommandRegistry
import com.smarthealth.vitalhub.foundation.device.protocol.RecorderProtocolV1
import com.smarthealth.vitalhub.foundation.device.protocol.RecorderProtocolV1Config
import com.smarthealth.vitalhub.foundation.device.transport.BluetoothChannelType
import com.smarthealth.vitalhub.foundation.device.transport.BluetoothDeviceProfile
import com.smarthealth.vitalhub.foundation.device.transport.BluetoothDeviceProfileResolver
import com.smarthealth.vitalhub.foundation.device.transport.BluetoothGattDeviceResolver
import com.smarthealth.vitalhub.foundation.device.transport.BluetoothGattDeviceTransport
import com.smarthealth.vitalhub.foundation.device.waveform.DefaultWaveformPipeline
import java.nio.ByteOrder
import java.util.UUID

enum class ProtocolByteOrder { BIG_ENDIAN, LITTLE_ENDIAN }

data class RecorderDeviceSdkConfig(
    val serviceUuid: String,
    val notifyCharacteristicUuid: String,
    val writeCharacteristicUuid: String,
    val scalarByteOrder: ProtocolByteOrder,
    val checksumIncludesHeader: Boolean,
    val useNotificationDescriptor: Boolean = false,
    val splitWrite: Boolean = true,
    val writeIntervalMillis: Long = 0,
    val dataChainTraceEnabled: Boolean = false,
)

data class RecorderDeviceProtocolConfig(
    val scalarByteOrder: ProtocolByteOrder,
    val checksumIncludesHeader: Boolean,
    val dataChainTraceEnabled: Boolean = false,
)

object RecorderDeviceSdk {
    private const val DATA_CHAIN_TAG = "RecorderDataChain"

    @SuppressLint("MissingPermission")
    fun create(config: RecorderDeviceSdkConfig): DeviceSdk {
        val profile = BluetoothDeviceProfile(
            serviceUuid = UUID.fromString(config.serviceUuid),
            notifyCharacteristicUuid = UUID.fromString(config.notifyCharacteristicUuid),
            writeCharacteristicUuid = UUID.fromString(config.writeCharacteristicUuid),
            useNotificationDescriptor = config.useNotificationDescriptor,
            splitWrite = config.splitWrite,
            writeIntervalMillis = config.writeIntervalMillis,
        )
        return createInternal(
            protocolConfig = RecorderDeviceProtocolConfig(
                scalarByteOrder = config.scalarByteOrder,
                checksumIncludesHeader = config.checksumIncludesHeader,
                dataChainTraceEnabled = config.dataChainTraceEnabled,
            ),
            profileResolver = BluetoothDeviceProfileResolver { profile },
        )
    }

    @SuppressLint("MissingPermission")
    fun createWithGattDiscovery(config: RecorderDeviceProtocolConfig): DeviceSdk = createInternal(
        protocolConfig = config,
        profileResolver = BluetoothDeviceProfileResolver(::discoverProfile),
    )

    @SuppressLint("MissingPermission")
    private fun createInternal(
        protocolConfig: RecorderDeviceProtocolConfig,
        profileResolver: BluetoothDeviceProfileResolver,
    ): DeviceSdk {
        val trace = object : DeviceTrace {
            override fun log(stage: String, message: String) {
                log(stage, message, null)
            }

            override fun log(stage: String, message: String, payload: ByteArray?) {
                DeviceDebugTrace.record(stage, message, payload)
                if (protocolConfig.dataChainTraceEnabled) {
                    Log.d(DATA_CHAIN_TAG, "[$stage] $message")
                }
            }
        }
        val resolver = BluetoothGattDeviceResolver { address ->
            val bluetoothDevice = BluetoothKit.getInstance().bluetoothAdapter.getRemoteDevice(address)
            BluetoothGattDevice(bluetoothDevice)
        }
        return DefaultDeviceSdk(
            transportFactory = { BluetoothGattDeviceTransport(profileResolver, resolver, trace) },
            protocolFactory = {
                RecorderProtocolV1.create(
                    RecorderProtocolV1Config(
                        scalarByteOrder = when (protocolConfig.scalarByteOrder) {
                            ProtocolByteOrder.BIG_ENDIAN -> ByteOrder.BIG_ENDIAN
                            ProtocolByteOrder.LITTLE_ENDIAN -> ByteOrder.LITTLE_ENDIAN
                        },
                        checksumIncludesHeader = protocolConfig.checksumIncludesHeader,
                        receiptCodes = RecorderCommandRegistry.receiptCodes,
                    ),
                    trace,
                )
            },
            commandEncoder = RecorderCommandEncoder(),
            waveformFactory = { DefaultWaveformPipeline(trace = trace) },
            trace = trace,
        )
    }

    private fun discoverProfile(device: BluetoothGattDevice): BluetoothDeviceProfile {
        val services = device.gattServices.orEmpty()
        val profile = services.firstNotNullOfOrNull { service ->
            val characteristics = service.characteristics
            val notify = characteristics.firstOrNull { characteristic ->
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            }
            val indicate = characteristics.firstOrNull { characteristic ->
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            }
            val dataChannel = notify ?: indicate ?: return@firstNotNullOfOrNull null
            val write = characteristics.firstOrNull { characteristic ->
                characteristic.properties and (
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    ) != 0
            } ?: return@firstNotNullOfOrNull null
            BluetoothDeviceProfile(
                serviceUuid = service.uuid,
                notifyCharacteristicUuid = dataChannel.uuid,
                writeCharacteristicUuid = write.uuid,
                useNotificationDescriptor = false,
                channelType = if (notify != null) {
                    BluetoothChannelType.NOTIFY
                } else {
                    BluetoothChannelType.INDICATE
                },
            )
        }
        return checkNotNull(profile) {
            val available = services.joinToString { service ->
                "${service.uuid}=[${service.characteristics.joinToString { it.uuid.toString() }}]"
            }
            "No GATT service contains both a notify/indicate and write characteristic. Available: $available"
        }.also { discovered ->
            Log.i(
                "RecorderDeviceSdk",
                "GATT profile: service=${discovered.serviceUuid}, " +
                    "receive=${discovered.notifyCharacteristicUuid}, " +
                    "write=${discovered.writeCharacteristicUuid}, " +
                    "type=${discovered.channelType}",
            )
        }
    }
}
