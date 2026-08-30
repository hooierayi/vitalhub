package com.smarthealth.vitalhub.feature.user.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        fun create(context: Context): UserDatabase = Room.databaseBuilder(
            context.applicationContext,
            UserDatabase::class.java,
            "vitalhub-users.db",
        )
            // The Provider contract is synchronous for route-time title decisions.
            // This table is intentionally tiny; writes remain suspend and transactional.
            .allowMainThreadQueries()
            .build()
    }
}
