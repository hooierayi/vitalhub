package com.smarthealth.vitalhub.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserInfoEditUiState(
    val name: String = "",
    val gender: Gender? = null,
    val age: String = "",
    val mode: UserInfoEditMode = UserInfoEditMode.CREATE,
    val validationError: String? = null,
)

enum class UserInfoEditMode { CREATE, EDIT }

class UserInfoEditViewModel(
    private val provider: UserInfoProvider? = resolveUserInfoProvider(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserInfoEditUiState())
    val uiState: StateFlow<UserInfoEditUiState> = _uiState.asStateFlow()

    init {
        provider?.getUser()?.let { user ->
            _uiState.value = UserInfoEditUiState(
                name = user.name,
                gender = user.gender,
                age = user.age.toString(),
                mode = UserInfoEditMode.EDIT,
            )
        }
    }

    fun updateName(name: String) = update { it.copy(name = name) }
    fun updateGender(gender: Gender) = update { it.copy(gender = gender) }
    fun updateAge(age: String) = update { it.copy(age = age.filter(Char::isDigit).take(3)) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val name = state.name.trim()
        val age = state.age.toIntOrNull()
        val error = when {
            name.isBlank() -> "请输入姓名"
            state.gender == null -> "请选择性别"
            age == null || age !in 1..150 -> "请输入 1 至 150 之间的年龄"
            else -> null
        }
        if (error != null) {
            _uiState.value = state.copy(validationError = error)
            return
        }
        val userInfoProvider = provider
        if (userInfoProvider == null) {
            _uiState.value = state.copy(validationError = "用户资料服务暂不可用")
            return
        }
        viewModelScope.launch {
            val saved = userInfoProvider.saveUser(UserInfo(name, requireNotNull(state.gender), requireNotNull(age)))
            if (saved) {
                onSaved()
            } else {
                _uiState.value = _uiState.value.copy(validationError = "保存失败，请稍后重试")
            }
        }
    }

    private fun update(transform: (UserInfoEditUiState) -> UserInfoEditUiState) {
        _uiState.value = transform(_uiState.value).copy(validationError = null)
    }
}

private fun resolveUserInfoProvider(): UserInfoProvider? = runCatching {
    ARouter.getInstance().navigation(UserInfoProvider::class.java)
}.getOrNull()
