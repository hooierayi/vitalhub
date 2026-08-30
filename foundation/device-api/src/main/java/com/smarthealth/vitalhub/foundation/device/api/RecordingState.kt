package com.smarthealth.vitalhub.foundation.device.api

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val targetPath: String) : RecordingState
    data class Completed(val targetPath: String) : RecordingState
    data class Failed(val cause: Throwable) : RecordingState
}
