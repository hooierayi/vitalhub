package com.smarthealth.vitalhub.foundation.file.protocol

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class LocalFileDicomPublisher(
    private val outputDirectory: File,
    private val fileNamePrefix: String,
) : DicomPublisher {
    override suspend fun createPart(descriptor: DicomInstanceDescriptor): DicomPartTarget {
        check(outputDirectory.exists() || outputDirectory.mkdirs()) {
            "Cannot create DICOM output directory: $outputDirectory"
        }
        val suffix = descriptor.segmentNumber.toString().padStart(3, '0')
        val finalFile = File(outputDirectory, "$fileNamePrefix-$suffix.dcm")
        val partFile = File(outputDirectory, "$fileNamePrefix-$suffix.dcm.part")
        check(!finalFile.exists() && !partFile.exists()) { "DICOM target already exists: $finalFile" }
        return LocalPartTarget(partFile, finalFile)
    }

    override suspend fun commit(target: DicomPartTarget): PublishedDicomFile {
        val local = target as? LocalPartTarget ?: error("Unsupported DICOM target: $target")
        check(local.partFile.exists()) { "DICOM part does not exist: ${local.partFile}" }
        check(!local.finalFile.exists()) { "DICOM target already exists: ${local.finalFile}" }
        val atomic = local.partFile.renameTo(local.finalFile)
        if (!atomic) {
            local.partFile.copyTo(local.finalFile, overwrite = false)
            check(local.partFile.delete()) { "Cannot remove committed DICOM part: ${local.partFile}" }
        }
        return PublishedDicomFile(
            displayName = local.finalFile.name,
            location = local.finalFile.absolutePath,
            sizeBytes = local.finalFile.length(),
            atomicCommit = atomic,
        )
    }

    override suspend fun discardPart(target: DicomPartTarget) {
        val local = target as? LocalPartTarget ?: return
        if (local.partFile.exists()) local.partFile.delete()
    }

    private data class LocalPartTarget(
        val partFile: File,
        val finalFile: File,
    ) : DicomPartTarget {
        override val displayName: String = finalFile.name
        override suspend fun openOutputStream(): OutputStream =
            BufferedOutputStream(FileOutputStream(partFile))
    }
}
