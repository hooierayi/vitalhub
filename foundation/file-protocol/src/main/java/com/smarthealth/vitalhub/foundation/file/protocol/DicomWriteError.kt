package com.smarthealth.vitalhub.foundation.file.protocol

sealed interface DicomWriteError {
    data class InvalidDefinition(
        val violations: List<DicomIssue>,
    ) : DicomWriteError

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
    ) : DicomWriteError

    data class WorkspaceCorrupted(val reason: String) : DicomWriteError
    data class TargetAlreadyExists(val target: String) : DicomWriteError
    data class EncodingFailed(val cause: Throwable) : DicomWriteError

    data class VerificationFailed(
        val violations: List<DicomIssue>,
    ) : DicomWriteError

    data class PublishingFailed(
        val cause: Throwable,
        val partRetained: Boolean,
    ) : DicomWriteError
}

data class DicomIssue(
    val code: String,
    val message: String,
)

class DicomWriteException(
    val error: DicomWriteError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)
