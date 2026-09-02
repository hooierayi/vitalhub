package com.smarthealth.vitalhub.foundation.file.protocol

data class DicomRecordingDefinition(
    val sessionId: String,
    val patient: PatientDefinition,
    val study: StudyDefinition,
    val series: SeriesDefinition,
    val equipment: EquipmentDefinition,
    val acquisition: AcquisitionDefinition,
    val signalLayout: WearableSignalLayout,
    val privateSchema: PrivateSchemaDefinition = PrivateSchemaDefinition.VITALHUB_DATA_V1,
    val writerPolicy: DicomWriterPolicy,
)

data class PatientDefinition(
    val patientName: PersonName?,
    val patientId: String,
    val issuerOfPatientId: String?,
    val birthDate: String?,
    val sex: PatientSex?,
    /** Used instead of inventing a birth date when only age is known. */
    val ageYears: Int? = null,
)

data class PersonName(
    val familyName: String?,
    val givenName: String?,
)

enum class PatientSex(val dicomCode: String) {
    MALE("M"),
    FEMALE("F"),
    OTHER("O"),
    UNKNOWN(""),
}

data class StudyDefinition(
    val studyInstanceUid: String,
    val studyId: String?,
    val studyDateTimeEpochMillis: Long,
    val accessionNumber: String?,
)

data class SeriesDefinition(
    val seriesInstanceUid: String,
    val seriesNumber: Int,
    val description: String = "VitalHub Wearable Aggregate Data",
    val modality: String = "OT",
)

data class EquipmentDefinition(
    val manufacturer: String,
    val modelName: String,
    val serialNumber: String?,
    val softwareVersions: List<String>,
)

data class AcquisitionDefinition(
    val startedAtEpochMillis: Long,
    /** DICOM offset in `+HHMM` or `-HHMM` form. */
    val timezoneOffset: String,
    val timeSynchronization: TimeSynchronization = TimeSynchronization.NotSynchronized,
)

sealed interface TimeSynchronization {
    object NotSynchronized : TimeSynchronization

    data class Synchronized(
        val timeSource: String,
        val synchronizationFrameOfReferenceUid: String,
    ) : TimeSynchronization
}

enum class PrivateSchemaDefinition(
    val creator: String,
    val schemaVersion: Int,
    val creatorVersionUid: String,
) {
    VITALHUB_DATA_V1(
        creator = "VITALHUB_DATA_V1",
        schemaVersion = 1,
        creatorVersionUid = "2.25.107124967801356309540880412951346319772",
    ),
}
