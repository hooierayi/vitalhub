package com.smarthealth.vitalhub.feature.home

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.viewModels
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navigation.AppBarDestination
import com.smarthealth.vitalhub.core.navigation.BottomNavigationDestination
import com.smarthealth.vitalhub.core.navigation.BottomNavigationKeys
import com.smarthealth.vitalhub.core.navigation.FlowNavigationHost
import com.smarthealth.vitalhub.core.navigation.Navigator
import com.smarthealth.vitalhub.core.navigation.QuestionnairePhase
import com.smarthealth.vitalhub.core.navigation.Routes
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

@Route(path = Routes.HOME)
class HomeFragment : BaseFlowFragment(), BottomNavigationDestination, AppBarDestination {
    private val viewModel by viewModels<HomeViewModel>()

    override val bottomNavigationKey = BottomNavigationKeys.COLLECTION
    override val appBarTitle = "采集任务"
    override val showAppBarBack = false
    override val showNotificationAction = true

    override fun onResume() {
        super.onResume()
        viewModel.refreshUser()
    }

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        HomeScreen(
            state = state,
            onStartQuestionnaire = {
                viewModel.startSession()?.let { sessionId ->
                    Navigator.questionnaire(requireActivity() as FlowNavigationHost, sessionId, QuestionnairePhase.PRE)
                }
            },
            onEditUserInfo = {
                Navigator.editUserInfo(requireActivity() as FlowNavigationHost)
            },
            onContinueStep = { stepNumber ->
                if (stepNumber == 1 && state.user == null) {
                    Navigator.editUserInfo(requireActivity() as FlowNavigationHost)
                } else {
                    val sessionId = viewModel.currentProgress()?.sessionId ?: if (stepNumber == 1) viewModel.startSession() else null
                    when (stepNumber) {
                        1 -> sessionId?.let { Navigator.questionnaire(requireActivity() as FlowNavigationHost, it, QuestionnairePhase.PRE) }
                        2 -> sessionId?.let { Navigator.device(requireActivity() as FlowNavigationHost, it) }
                        3 -> sessionId?.let { Navigator.collection(requireActivity() as FlowNavigationHost, it, com.smarthealth.vitalhub.core.navigation.CollectionMode.PREVIEW) }
                        4 -> sessionId?.let { Navigator.questionnaire(requireActivity() as FlowNavigationHost, it, QuestionnairePhase.POST) }
                    }
                }
            },
        )
    }
}
