package com.smarthealth.vitalhub.foundation.file.protocol

import java.io.File
import java.io.OutputStream

data class DicomWorkspace(val directory: File)

interface DicomPublisher {
    suspend fun createPart(descriptor: DicomInstanceDescriptor): DicomPartTarget
    suspend fun commit(target: DicomPartTarget): PublishedDicomFile
    suspend fun discardPart(target: DicomPartTarget)
}

interface DicomPartTarget {
    val displayName: String
    suspend fun openOutputStream(): OutputStream
}

data class DicomInstanceDescriptor(
    val sessionId: String,
    val segmentNumber: Int,
    val studyInstanceUid: String,
    val seriesInstanceUid: String,
    val sopInstanceUid: String,
    val instanceNumber: Int,
)

data class PublishedDicomFile(
    val displayName: String,
    /** Opaque publisher-specific locator; it is not written into the DICOM dataset. */
    val location: String,
    val sizeBytes: Long,
    val atomicCommit: Boolean,
)
