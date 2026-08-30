package com.smarthealth.vitalhub.feature.user

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.feature.user.data.UserDatabase
import com.smarthealth.vitalhub.feature.user.data.UserStatus
import com.smarthealth.vitalhub.feature.user.data.toEntity
import com.smarthealth.vitalhub.feature.user.data.toModel
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider

/** Room-backed owner of the active and historical user profiles. */
@Route(path = Routes.USER_INFO_PROVIDER)
class UserInfoProviderImpl : UserInfoProvider {
    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var database: UserDatabase? = null

    override fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    override fun getUser(): UserInfo? = requireDatabase()
        .userDao()
        .getActive()
        ?.toModel()

    override fun getUser(fingerprint: String): UserInfo? {
        if (fingerprint.isBlank()) return null
        return requireDatabase().userDao().getByFingerprint(fingerprint)?.toModel()
    }

    override suspend fun saveUser(user: UserInfo): Boolean {
        val normalized = user.copy(name = user.name.trim())
        require(normalized.name.isNotBlank()) { "User name must not be blank." }
        require(normalized.gender != Gender.UNSPECIFIED) { "User gender must be selected." }
        require(normalized.age in MIN_AGE..MAX_AGE) {
            "User age must be between $MIN_AGE and $MAX_AGE."
        }
        return runCatching {
            requireDatabase().userDao().replaceActive(normalized.toEntity(UserStatus.ACTIVE))
            true
        }.getOrDefault(false)
    }

    private fun requireDatabase(): UserDatabase = database ?: synchronized(this) {
        database ?: UserDatabase.create(
            checkNotNull(applicationContext) {
                "UserInfoProviderImpl must be initialized by ARouter before use."
            },
        ).also { database = it }
    }

    private companion object {
        const val MIN_AGE = 1
        const val MAX_AGE = 150
    }
}
