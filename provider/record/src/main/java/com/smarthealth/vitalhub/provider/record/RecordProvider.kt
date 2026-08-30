package com.smarthealth.vitalhub.provider.record

import com.alibaba.android.arouter.facade.template.IProvider
import kotlinx.coroutines.flow.Flow

/** Durable completed-collection records shared across application modules. */
interface RecordProvider : IProvider {
    /** Emits all completed records, newest completion first. */
    fun observeAllRecords(): Flow<List<CollectionRecord>>

    suspend fun getAllRecords(): List<CollectionRecord>

    /** Inserts or replaces the record identified by [CollectionRecord.id]. */
    suspend fun saveRecord(record: CollectionRecord): Boolean
}

data class CollectionRecord(
    val id: String,
    val sessionId: String,
    val type: RecordType,
    val recordedAtEpochMillis: Long,
    val durationMillis: Long,
    val localFilePath: String?,
    val userFingerprint: String,
    val deviceAddress: String,
)

enum class RecordType {
    CLIP,
    CONTINUOUS,
}
