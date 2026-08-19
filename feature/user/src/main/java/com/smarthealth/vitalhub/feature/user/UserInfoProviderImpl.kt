package com.smarthealth.vitalhub.feature.user

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.core.storage.Storage
import com.smarthealth.vitalhub.core.storage.StorageBackend
import com.smarthealth.vitalhub.core.storage.StorageOptions
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider

/** User-profile storage backed by the shared KV storage module. */
@Route(path = Routes.USER_INFO_PROVIDER)
class UserInfoProviderImpl() : UserInfoProvider {
    @Volatile
    private var storage: KVStorage? = null

    internal constructor(storage: KVStorage) : this() {
        this.storage = storage
    }

    override fun init(context: Context) {
        storage = Storage.create(
            context = context.applicationContext,
            name = STORAGE_ID,
            options = StorageOptions(backend = StorageBackend.MMKV),
        )
    }

    override fun getUser(): UserInfo? {
        val name = requireStorage().getString(KEY_NAME)?.trim().orEmpty()
        val age = requireStorage().getInt(KEY_AGE, INVALID_AGE)
        if (name.isBlank() || age !in MIN_AGE..MAX_AGE) return null
        val gender = requireStorage().getString(KEY_GENDER)
            ?.let { value -> Gender.entries.firstOrNull { it.name == value } }
            ?: return null
        if (gender == Gender.UNSPECIFIED) return null
        return UserInfo(name, gender, age)
    }

    override suspend fun saveUser(user: UserInfo): Boolean {
        require(user.name.isNotBlank()) { "User name must not be blank." }
        require(user.gender != Gender.UNSPECIFIED) { "User gender must be selected." }
        require(user.age in MIN_AGE..MAX_AGE) { "User age must be between $MIN_AGE and $MAX_AGE." }
        return requireStorage().edit()
            .putString(KEY_NAME, user.name)
            .putString(KEY_GENDER, user.gender.name)
            .putInt(KEY_AGE, user.age)
            .commit()
    }

    private fun requireStorage(): KVStorage = checkNotNull(storage) {
        "UserInfoProviderImpl must be initialized by ARouter before use."
    }

    private companion object {
        const val STORAGE_ID = "vitalhub-user-info"
        const val KEY_NAME = "name"
        const val KEY_GENDER = "gender"
        const val KEY_AGE = "age"
        const val INVALID_AGE = -1
        const val MIN_AGE = 1
        const val MAX_AGE = 150
    }
}
