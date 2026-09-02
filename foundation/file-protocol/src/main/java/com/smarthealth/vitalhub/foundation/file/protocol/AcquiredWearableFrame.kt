package com.smarthealth.vitalhub.foundation.file.protocol

data class AcquiredWearableFrame(
    val ecg: IntArray,
    val respiration: IntArray,
    val motion: List<WearableMotionSample>,
    /** Raw signed 16-bit value; physical value is raw * 0.01 degrees Celsius. */
    val skinTemperatureRaw: Int,
    /** Raw signed 16-bit value; physical value is raw * 0.01 degrees Celsius. */
    val ambientTemperatureRaw: Int,
    /** Raw signed 16-bit value; physical value is raw * 0.01 percent. */
    val ambientHumidityRaw: Int,
    val sweatLevel: SweatLevelValue,
)

data class WearableMotionSample(
    val gyroX: Int,
    val gyroY: Int,
    val gyroZ: Int,
    val accelerationX: Int,
    val accelerationY: Int,
    val accelerationZ: Int,
)

enum class SweatLevelValue(val encodedValue: Int) {
    UNKNOWN(0),
    NONE(1),
    LIGHT(2),
    MEDIUM(3),
    HEAVY(4),
}
