package com.smarthealth.vitalhub.provider.user

import com.alibaba.android.arouter.facade.template.IProvider

/**
 * Provides the participant currently selected for collection.
 *
 * A [null] value means that no participant is currently available.
 */
interface UserInfoProvider : IProvider {
    /**
     * Returns the complete locally stored profile, or null when no valid profile exists.
     * This is a lightweight MMKV lookup and is safe for route-time title decisions.
     */
    fun getUser(): UserInfo?

    /**
     * Persists [user] and reports whether the storage implementation accepted the complete update.
     */
    suspend fun saveUser(user: UserInfo): Boolean
}
