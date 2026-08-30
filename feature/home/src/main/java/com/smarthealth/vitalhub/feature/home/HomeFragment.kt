package com.smarthealth.vitalhub.feature.home

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.viewModels
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.AppBarDestination
import com.smarthealth.vitalhub.core.navi.BottomNavigationDestination
import com.smarthealth.vitalhub.core.navi.BottomNavigationKeys
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.FlowDestinationContext
import com.smarthealth.vitalhub.core.navi.FlowDestinationOwner
import com.smarthealth.vitalhub.core.navi.FlowEntryMode
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

@Route(path = Routes.HOME)
class HomeFragment : BaseFlowFragment(), BottomNavigationDestination, AppBarDestination, FlowDestinationOwner {
    private val viewModel by viewModels<HomeViewModel>()

    override val bottomNavigationKey = BottomNavigationKeys.COLLECTION
    override val appBarTitle = "采集任务"
    override val showAppBarBack = false
    override val showNotificationAction = true
    override val flowDestinationContext = FlowDestinationContext(FlowDestination.HOME)

    override fun onResume() {
        super.onResume()
        viewModel.refreshUser()
    }

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        HomeScreen(
            state = state,
            onPrimaryAction = {
                if (state.user == null) {
                    Navigator.editUserInfo(requireContext())
                } else {
                    viewModel.sessionForSequentialEntry()?.let { session ->
                        Navigator.flow(
                            requireContext(),
                            session.sessionId,
                            FlowDestination.PRE_QUESTIONNAIRE,
                        )
                    }
                }
            },
            onEditUserInfo = {
                Navigator.editUserInfo(requireContext())
            },
            onContinueStep = { stepNumber ->
                viewModel.sessionForDirectEntry()?.let { session ->
                    val destination = when (stepNumber) {
                        1 -> FlowDestination.PRE_QUESTIONNAIRE
                        2 -> FlowDestination.DEVICE_CONNECTION
                        else -> FlowDestination.POST_QUESTIONNAIRE
                    }
                    Navigator.flow(
                        requireContext(),
                        session.sessionId,
                        destination,
                        FlowEntryMode.DIRECT_RETURN_HOME,
                    )
                }
            },
        )
    }
}
