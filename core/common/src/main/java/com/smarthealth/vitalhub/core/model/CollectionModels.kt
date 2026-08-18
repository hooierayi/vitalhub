package com.smarthealth.vitalhub.core.model

data class CollectionSession(
    val sessionId: String,
    val deviceId: String? = null,
    val recordId: String? = null,
    val stage: SessionStage = SessionStage.CREATED,
)

enum class SessionStage {
    CREATED, PRE_QUESTIONNAIRE_COMPLETED, DEVICE_CONNECTED, PREVIEWING,
    CLIP_CACHED, UPLOADING, ANALYZING, CONTINUOUS_RECORDING,
    POST_QUESTIONNAIRE_COMPLETED, COMPLETED, FAILED,
}

enum class DeviceState {
    DISCONNECTED, CONNECTED_IDLE, PREVIEWING, CONTINUOUS_RECORDING,
    CONTINUOUS_RECORDING_WITH_PREVIEW, FAULT,
}
