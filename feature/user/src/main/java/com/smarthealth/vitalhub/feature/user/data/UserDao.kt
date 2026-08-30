package com.smarthealth.vitalhub.feature.user.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface UserDao {
    @Query("SELECT * FROM users WHERE status = 'active' LIMIT 1")
    fun getActive(): UserEntity?

    @Query("SELECT * FROM users WHERE fingerprint = :fingerprint LIMIT 1")
    fun getByFingerprint(fingerprint: String): UserEntity?

    @Query("UPDATE users SET status = 'inactive' WHERE status = 'active'")
    suspend fun deactivateActive()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Transaction
    suspend fun replaceActive(user: UserEntity) {
        deactivateActive()
        upsert(user.copy(status = UserStatus.ACTIVE.storedValue))
    }
}
