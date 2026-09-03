package com.smarthealth.vitalhub.feature.collection.record

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CollectionRecordDao {
    @Query("SELECT * FROM collection_records ORDER BY recordedAtEpochMillis DESC")
    fun observeAll(): Flow<List<CollectionRecordEntity>>

    @Query("SELECT * FROM collection_records ORDER BY recordedAtEpochMillis DESC")
    suspend fun getAll(): List<CollectionRecordEntity>

    @Query(
        "SELECT * FROM collection_records WHERE sessionId = :sessionId " +
            "ORDER BY recordedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun getBySessionId(sessionId: String): CollectionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CollectionRecordEntity): Long

    @Query(
        "UPDATE collection_records SET analysisId = :analysisId WHERE id = :recordId",
    )
    suspend fun updateAnalysisId(
        recordId: String,
        analysisId: String?,
    ): Int
}
