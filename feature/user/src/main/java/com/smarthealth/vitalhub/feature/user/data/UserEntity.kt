package com.smarthealth.vitalhub.feature.user.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo

@Entity(
    tableName = "users",
    indices = [Index(value = ["status"])],
)
internal data class UserEntity(
    @PrimaryKey val fingerprint: String,
    val name: String,
    val gender: String,
    val age: Int,
    val status: String,
)

internal enum class UserStatus(val storedValue: String) {
    ACTIVE("active"),
    INACTIVE("inactive"),
}

internal fun UserInfo.toEntity(status: UserStatus) = UserEntity(
    fingerprint = fingerprint,
    name = name.trim(),
    gender = gender.name,
    age = age,
    status = status.storedValue,
)

internal fun UserEntity.toModel(): UserInfo? {
    val parsedGender = Gender.entries.firstOrNull { it.name == gender }
        ?.takeUnless { it == Gender.UNSPECIFIED }
        ?: return null
    return UserInfo(name = name, gender = parsedGender, age = age)
        .takeIf { it.fingerprint == fingerprint }
}
