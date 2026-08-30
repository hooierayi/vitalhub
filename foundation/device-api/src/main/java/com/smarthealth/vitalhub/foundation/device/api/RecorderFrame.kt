package com.smarthealth.vitalhub.foundation.device.api

enum class FrameContinuity {
    FIRST,
    CONTINUOUS,
    GAP,
    DUPLICATE,
    OUT_OF_ORDER,
}

data class FrameMetadata(
    val sequence: Int,
    val receivedAtMillis: Long,
    val continuity: FrameContinuity,
    val protocolVersion: Int,
)

data class TemperatureBlock(
    val skinCelsius: Double,
    val ambientCelsius: Double,
    val humidityPercent: Double,
)

data class MotionSample(
    val gyroX: Short,
    val gyroY: Short,
    val gyroZ: Short,
    val accelerationX: Short,
    val accelerationY: Short,
    val accelerationZ: Short,
)

enum class SweatLevel { UNKNOWN, NONE, LIGHT, MEDIUM, HEAVY }

data class RecorderFrame(
    val metadata: FrameMetadata,
    val ecg: IntArray,
    val respiration: IntArray,
    val temperature: TemperatureBlock,
    val motion: List<MotionSample>,
    val sweatLevel: SweatLevel,
    val leadOff: Boolean,
)

data class EcgWaveformFrame(
    val sequence: Int,
    val samples: IntArray,
    val continuity: FrameContinuity,
)

data class RespirationWaveformFrame(
    val sequence: Int,
    val samples: IntArray,
    val continuity: FrameContinuity,
)

data class DeviceMetrics(
    val sequence: Int,
    val skinCelsius: Double,
    val ambientCelsius: Double,
    val humidityPercent: Double,
    val sweatLevel: SweatLevel,
    val leadOff: Boolean,
)
