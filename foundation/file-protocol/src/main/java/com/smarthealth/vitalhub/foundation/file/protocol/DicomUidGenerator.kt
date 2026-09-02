package com.smarthealth.vitalhub.foundation.file.protocol

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID

/** Generates globally unique DICOM UIDs without embedding business meaning in their values. */
interface DicomUidGenerator {
    fun newStudyInstanceUid(): String
    fun newSeriesInstanceUid(): String
    fun newSopInstanceUid(): String
}

class UuidDicomUidGenerator : DicomUidGenerator {
    override fun newStudyInstanceUid(): String = newUid()
    override fun newSeriesInstanceUid(): String = newUid()
    override fun newSopInstanceUid(): String = newUid()

    private fun newUid(): String {
        val uuid = UUID.randomUUID()
        val bytes = ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return "2.25.${BigInteger(1, bytes)}"
    }
}
