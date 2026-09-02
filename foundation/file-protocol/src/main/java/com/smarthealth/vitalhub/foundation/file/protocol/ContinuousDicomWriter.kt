package com.smarthealth.vitalhub.foundation.file.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ContinuousDicomWriterFactory {
    suspend fun create(
        definition: DicomRecordingDefinition,
        workspace: DicomWorkspace,
        publisher: DicomPublisher,
    ): ContinuousDicomWriter

    suspend fun inspect(workspace: DicomWorkspace): DicomWorkspaceStatus

    suspend fun resume(
        workspace: DicomWorkspace,
        publisher: DicomPublisher,
    ): ContinuousDicomWriter
}

interface ContinuousDicomWriter {
    val sessionId: String
    val state: StateFlow<DicomWriterState>
    val completedSegments: Flow<DicomSegmentResult>

    /**
     * Appends one complete one-second aggregate frame in call order.
     *
     * A successful return means that the writer has accepted the values. It does not imply that a
     * final DICOM Part 10 file has already been generated; use [checkpoint] for crash durability
     * and [finish] to publish the final file.
     */
    suspend fun append(frame: AcquiredWearableFrame): AppendReceipt

    /** Makes all committed frames durable and recoverable. */
    suspend fun checkpoint(): DicomCheckpoint

    /** Finalizes and publishes the remaining segment. Successful calls are idempotent. */
    suspend fun finish(): DicomRecordingResult

    /** Aborts the open segment without deleting already published segments. */
    suspend fun abort(
        reason: AbortReason = AbortReason.USER_REQUESTED,
        retainWorkspace: Boolean = true,
    )
}

enum class AbortReason {
    USER_REQUESTED,
    RECORDING_CANCELLED,
    UNRECOVERABLE_ERROR,
}
