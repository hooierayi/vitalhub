package com.smarthealth.vitalhub.feature.collection.record

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.CollectionAnalysis
import com.smarthealth.vitalhub.provider.record.RecordProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed owner of completed collection records. */
@Route(path = Routes.RECORD_PROVIDER)
class RecordProviderImpl : RecordProvider {
    @Volatile
    private var applicationContext: Context? = null
    @Volatile
    private var database: CollectionRecordDatabase? = null

    override fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    override fun observeAllRecords(): Flow<List<CollectionRecord>> = requireDatabase()
        .collectionRecordDao()
        .observeAll()
        .map { records -> records.map(CollectionRecordEntity::toModel) }

    override suspend fun getAllRecords(): List<CollectionRecord> = requireDatabase()
        .collectionRecordDao()
        .getAll()
        .map(CollectionRecordEntity::toModel)

    override suspend fun getRecordBySessionId(sessionId: String): CollectionRecord? =
        requireDatabase().collectionRecordDao().getBySessionId(sessionId)?.toModel()

    override suspend fun saveRecord(record: CollectionRecord): Boolean = runCatching {
        require(record.id.isNotBlank()) { "Record id must not be blank." }
        require(record.sessionId.isNotBlank()) { "Session id must not be blank." }
        require(record.userFingerprint.isNotBlank()) { "User fingerprint must not be blank." }
        require(record.deviceAddress.isNotBlank()) { "Device address must not be blank." }
        requireDatabase().collectionRecordDao().insert(record.toEntity()) >= 0L
    }.getOrDefault(false)

    override suspend fun saveAnalysis(
        recordId: String,
        analysis: CollectionAnalysis,
    ): Boolean = runCatching {
        require(recordId.isNotBlank()) { "Record id must not be blank." }
        requireDatabase().collectionRecordDao().updateAnalysis(
            recordId = recordId,
            analysisId = analysis.analysisId,
            status = analysis.status.name,
            resultMarkdown = analysis.resultMarkdown,
            errorCode = analysis.errorCode,
            errorMessage = analysis.errorMessage,
            updatedAtEpochMillis = analysis.updatedAtEpochMillis,
        ) > 0
    }.getOrDefault(false)

    private fun requireDatabase(): CollectionRecordDatabase {
        database?.let { return it }
        return synchronized(this) {
            database ?: CollectionRecordDatabase.create(
                checkNotNull(applicationContext) {
                    "RecordProviderImpl must be initialized by ARouter before use."
                },
            ).also { database = it }
        }
    }
}
