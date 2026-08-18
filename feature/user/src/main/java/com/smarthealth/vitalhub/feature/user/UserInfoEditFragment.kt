package com.smarthealth.vitalhub.feature.user

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navigation.AppBarDestination
import com.smarthealth.vitalhub.core.navigation.Routes
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment
import com.smarthealth.vitalhub.provider.user.UserInfoProvider

@Route(path = Routes.USER_INFO_EDIT)
class UserInfoEditFragment : BaseFlowFragment(), AppBarDestination {
    private val viewModel by viewModels<UserInfoEditViewModel>()
    private val userInfoProvider: UserInfoProvider? by lazy {
        runCatching { ARouter.getInstance().navigation(UserInfoProvider::class.java) }.getOrNull()
    }
    private val hasExistingUser: Boolean by lazy { userInfoProvider?.getUser() != null }

    override val appBarTitle: String
        get() = if (hasExistingUser) {
            "修改用户信息"
        } else {
            "填写用户信息"
        }

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        UserInfoEditScreen(
            state = state,
            onNameChanged = viewModel::updateName,
            onGenderChanged = viewModel::updateGender,
            onAgeChanged = viewModel::updateAge,
            onSave = {
                viewModel.save {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            },
        )
    }
}
