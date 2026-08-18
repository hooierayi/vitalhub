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

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        HomeScreen(state, onStartQuestionnaire = {
            Navigator.questionnaire(requireActivity() as FlowNavigationHost, viewModel.startSession(), QuestionnairePhase.PRE)
        })
    }
}
