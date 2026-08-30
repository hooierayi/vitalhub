package com.smarthealth.vitalhub.feature.collection.record

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CollectionRecordEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class CollectionRecordDatabase : RoomDatabase() {
    abstract fun collectionRecordDao(): CollectionRecordDao

    companion object {
        fun create(context: Context): CollectionRecordDatabase = Room.databaseBuilder(
            context.applicationContext,
            CollectionRecordDatabase::class.java,
            "vitalhub-records.db",
        )
            .build()
    }
}
