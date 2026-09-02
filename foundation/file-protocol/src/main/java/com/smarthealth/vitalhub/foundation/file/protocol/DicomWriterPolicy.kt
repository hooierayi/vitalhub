package com.smarthealth.vitalhub.foundation.file.protocol

data class DicomWriterPolicy(
    val queueCapacity: Int = 32,
    val checkpointEveryFrames: Int = 30,
    val checkpointIntervalMillis: Long = 30_000L,
    val rollover: DicomRolloverPolicy,
    val verification: DicomVerificationPolicy = DicomVerificationPolicy.STRICT,
    val retainWorkspaceOnFailure: Boolean = true,
)

data class DicomRolloverPolicy(
    val maxEstimatedFileBytes: Long = 512L * 1024 * 1024,
)

enum class DicomVerificationPolicy { BASIC, STRICT }
