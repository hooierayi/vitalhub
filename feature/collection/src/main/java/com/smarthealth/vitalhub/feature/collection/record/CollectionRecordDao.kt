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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CollectionRecordEntity): Long
}
