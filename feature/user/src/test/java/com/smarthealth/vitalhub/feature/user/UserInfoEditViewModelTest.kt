package com.smarthealth.vitalhub.feature.user

import android.content.Context
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserInfoEditViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty provider produces blank create form`() {
        val viewModel = UserInfoEditViewModel(FakeUserInfoProvider(null))

        assertEquals(UserInfoEditMode.CREATE, viewModel.uiState.value.mode)
        assertEquals("", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.gender)
        assertEquals("", viewModel.uiState.value.age)
    }

    @Test
    fun `complete provider produces populated edit form`() {
        val user = UserInfo("王小明", Gender.FEMALE, 26)
        val viewModel = UserInfoEditViewModel(FakeUserInfoProvider(user))

        assertEquals(UserInfoEditMode.EDIT, viewModel.uiState.value.mode)
        assertEquals("王小明", viewModel.uiState.value.name)
        assertEquals(Gender.FEMALE, viewModel.uiState.value.gender)
        assertEquals("26", viewModel.uiState.value.age)
    }

    @Test
    fun `missing name gender or valid age cannot save`() {
        val provider = FakeUserInfoProvider(null)
        val viewModel = UserInfoEditViewModel(provider)

        viewModel.save { throw AssertionError("must not save") }
        assertEquals("请输入姓名", viewModel.uiState.value.validationError)

        viewModel.updateName("王小明")
        viewModel.save { throw AssertionError("must not save") }
        assertEquals("请选择性别", viewModel.uiState.value.validationError)

        viewModel.updateGender(Gender.MALE)
        viewModel.updateAge("151")
        viewModel.save { throw AssertionError("must not save") }
        assertEquals("请输入 1 至 150 之间的年龄", viewModel.uiState.value.validationError)
        assertFalse(provider.saved)
    }

    @Test
    fun `complete user can save`() {
        val provider = FakeUserInfoProvider(null)
        val viewModel = UserInfoEditViewModel(provider)
        var callbackCalled = false

        viewModel.updateName("王小明")
        viewModel.updateGender(Gender.MALE)
        viewModel.updateAge("32")
        viewModel.save { callbackCalled = true }

        assertTrue(callbackCalled)
        assertEquals(UserInfo("王小明", Gender.MALE, 32), provider.currentUser)
    }
}

private class FakeUserInfoProvider(
    var currentUser: UserInfo?,
) : UserInfoProvider {
    var saved = false

    override fun init(context: Context) = Unit

    override fun getUser(): UserInfo? = currentUser

    override suspend fun saveUser(user: UserInfo): Boolean {
        saved = true
        currentUser = user
        return true
    }
}
