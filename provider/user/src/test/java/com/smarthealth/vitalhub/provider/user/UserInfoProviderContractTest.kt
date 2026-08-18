package com.smarthealth.vitalhub.provider.user

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInfoProviderContractTest {
    @Test
    fun `current user can be read`() = runBlocking {
        val expected = UserInfo(name = "张三", gender = Gender.MALE, age = 32)
        val provider = FakeUserInfoProvider(expected)

        assertEquals(expected, provider.getUser())
    }

    @Test
    fun `missing current user is represented by null`() = runBlocking {
        val provider = FakeUserInfoProvider(null)

        assertNull(provider.getUser())
    }

    @Test
    fun `saved user can be read`() = runBlocking {
        val saved = UserInfo(name = "李四", gender = Gender.FEMALE, age = 28)
        val provider = FakeUserInfoProvider(null)

        val savedSuccessfully = provider.saveUser(saved)

        assertTrue(savedSuccessfully)
        assertEquals(saved, provider.getUser())
    }
}

private class FakeUserInfoProvider(
    private var currentUser: UserInfo?,
) : UserInfoProvider {
    override fun init(context: Context) = Unit

    override fun getUser(): UserInfo? = currentUser

    override suspend fun saveUser(user: UserInfo): Boolean {
        currentUser = user
        return true
    }
}
