package com.smarthealth.vitalhub.foundation.device.api

data class ContinuousCollectionSubject(
    val name: String,
    val genderCode: Int,
    val age: Int,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
)

sealed interface DeviceCommand {
    data object StartCollection : DeviceCommand
    data object StopCollection : DeviceCommand
    data class StartContinuous(val subject: ContinuousCollectionSubject) : DeviceCommand
}

sealed interface CommandResult {
    data object Success : CommandResult
    data class Rejected(val status: Int) : CommandResult
    data class Failed(val cause: Throwable) : CommandResult
}
