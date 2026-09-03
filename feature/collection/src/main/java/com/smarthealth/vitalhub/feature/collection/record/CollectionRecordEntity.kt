package com.smarthealth.vitalhub.feature.collection.record

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordType

@Entity(
    tableName = "collection_records",
    indices = [
        Index(value = ["recordedAtEpochMillis"]),
        Index(value = ["sessionId"]),
        Index(value = ["userFingerprint"]),
        Index(value = ["deviceAddress"]),
    ],
)
internal data class CollectionRecordEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val recordedAtEpochMillis: Long,
    val durationMillis: Long,
    val localFilePath: String?,
    val userFingerprint: String,
    val deviceAddress: String,
    val analysisId: String?,
)

internal fun CollectionRecord.toEntity() = CollectionRecordEntity(
    id = id,
    sessionId = sessionId,
    type = type.name,
    recordedAtEpochMillis = recordedAtEpochMillis,
    durationMillis = durationMillis,
    localFilePath = localFilePath,
    userFingerprint = userFingerprint,
    deviceAddress = deviceAddress,
    analysisId = analysisId,
)

internal fun CollectionRecordEntity.toModel() = CollectionRecord(
    id = id,
    sessionId = sessionId,
    type = RecordType.valueOf(type),
    recordedAtEpochMillis = recordedAtEpochMillis,
    durationMillis = durationMillis,
    localFilePath = localFilePath,
    userFingerprint = userFingerprint,
    deviceAddress = deviceAddress,
    analysisId = analysisId,
)
