package com.smarthealth.vitalhub.foundation.file.protocol

/** Runtime acknowledgement only; none of these fields are written into the DICOM dataset. */
data class AppendReceipt(
    val segmentNumber: Int,
    val acceptedFrameCount: Long,
    val rollover: RolloverResult?,
)

data class RolloverResult(
    val completedSegment: DicomSegmentResult,
    val nextSegmentNumber: Int,
)

data class DicomCheckpoint(
    val sessionId: String,
    val segmentNumber: Int,
    val durableFrameCount: Long,
    val createdAtEpochMillis: Long,
)

data class DicomRecordingResult(
    val sessionId: String,
    val segments: List<DicomSegmentResult>,
    val totalFrameCount: Long,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
)

data class DicomSegmentResult(
    val segmentNumber: Int,
    val studyInstanceUid: String,
    val seriesInstanceUid: String,
    val sopInstanceUid: String,
    val instanceNumber: Int,
    val frameCount: Long,
    val ecgSampleCount: Long,
    val respirationSampleCount: Long,
    val motionSampleCount: Long,
    val acquisitionStartedAtEpochMillis: Long,
    val acquisitionEndedAtEpochMillis: Long,
    val publishedFile: PublishedDicomFile,
    val checksum: FileChecksum,
)

data class FileChecksum(
    val algorithm: String,
    val value: String,
)
