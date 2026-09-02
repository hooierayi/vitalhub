package com.smarthealth.vitalhub.foundation.file.protocol

sealed interface DicomWriterState {
    object Creating : DicomWriterState

    data class Open(
        val segmentNumber: Int,
        val committedFrameCount: Long,
        val durableFrameCount: Long,
    ) : DicomWriterState

    data class Finalizing(
        val segmentNumber: Int,
        val phase: FinalizePhase,
    ) : DicomWriterState

    data class Completed(val result: DicomRecordingResult) : DicomWriterState

    data class Failed(
        val error: DicomWriteError,
        val recoverable: Boolean,
        val lastCheckpoint: DicomCheckpoint?,
    ) : DicomWriterState

    data class Aborted(val workspaceRetained: Boolean) : DicomWriterState
}

enum class FinalizePhase {
    FLUSHING,
    BUILDING_DATASET,
    WRITING_PART10,
    VERIFYING,
    PUBLISHING,
}

sealed interface DicomWorkspaceStatus {
    object Empty : DicomWorkspaceStatus

    data class Recoverable(
        val sessionId: String,
        val segmentNumber: Int,
        val committedFrameCount: Long,
        val durableFrameCount: Long,
        val lastCheckpoint: DicomCheckpoint,
    ) : DicomWorkspaceStatus

    data class Completed(val result: DicomRecordingResult) : DicomWorkspaceStatus

    data class Corrupted(val violations: List<DicomIssue>) : DicomWorkspaceStatus
}
