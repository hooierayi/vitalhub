package com.smarthealth.vitalhub.feature.questionnaire

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navigation.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

@Route(path = Routes.QUESTIONNAIRE)
class QuestionnaireFragment : BaseFlowFragment(), AppBarDestination {
    private val viewModel by viewModels<QuestionnaireViewModel>()

    override val appBarTitle: String
        get() = if (arguments?.getString(RouteArgs.QUESTIONNAIRE_PHASE) == QuestionnairePhase.POST) "采集后问卷" else "采集前问卷"

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        QuestionnaireScreen(
            state = state,
            onAnswer = viewModel::answer,
            onPrevious = viewModel::previousPage,
            onNext = {
                when (viewModel.nextPage()) {
                    QuestionnaireNext.DEVICE -> Navigator.device(requireActivity() as FlowNavigationHost, state.sessionId)
                    QuestionnaireNext.ANALYSIS -> Navigator.analysis(requireActivity() as FlowNavigationHost, state.sessionId)
                    QuestionnaireNext.STAY -> Unit
                }
            },
        )
    }
}
