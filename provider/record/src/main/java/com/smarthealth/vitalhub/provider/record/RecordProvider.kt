package com.smarthealth.vitalhub.provider.record

import com.alibaba.android.arouter.facade.template.IProvider
import kotlinx.coroutines.flow.Flow

/** Durable completed-collection records shared across application modules. */
interface RecordProvider : IProvider {
    /** Emits all completed records, newest completion first. */
    fun observeAllRecords(): Flow<List<CollectionRecord>>

    suspend fun getAllRecords(): List<CollectionRecord>

    suspend fun getRecordBySessionId(sessionId: String): CollectionRecord?

    /** Inserts or replaces the record identified by [CollectionRecord.id]. */
    suspend fun saveRecord(record: CollectionRecord): Boolean

    /** Persists the latest server-side upload/analysis state for a completed record. */
    suspend fun saveAnalysis(recordId: String, analysis: CollectionAnalysis): Boolean
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
    val analysis: CollectionAnalysis? = null,
)

data class CollectionAnalysis(
    val analysisId: String?,
    val status: AnalysisStatus,
    val resultMarkdown: String? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val updatedAtEpochMillis: Long,
)

enum class AnalysisStatus {
    UPLOADING,
    QUEUED,
    PROCESSING,
    RETRYING,
    COMPLETED,
    FAILED,
}

enum class RecordType {
    CLIP,
    CONTINUOUS,
}
