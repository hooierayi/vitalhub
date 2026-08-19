package com.smarthealth.vitalhub.feature.questionnaire

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition

@Route(path = Routes.QUESTIONNAIRE)
class QuestionnaireFragment : BaseFlowFragment(), AppBarDestination, FlowDestinationOwner {
    private val viewModel by viewModels<QuestionnaireViewModel>()

    override val appBarTitle: String
        get() = if (arguments?.getString(RouteArgs.QUESTIONNAIRE_PHASE) == QuestionnairePhase.POST) "采集后问卷" else "采集前问卷"
    override val flowDestinationContext: FlowDestinationContext
        get() = FlowDestinationContext(
            destination = if (arguments?.getString(RouteArgs.QUESTIONNAIRE_PHASE) == QuestionnairePhase.POST) {
                FlowDestination.POST_QUESTIONNAIRE
            } else {
                FlowDestination.PRE_QUESTIONNAIRE
            },
            sessionId = arguments?.getString(RouteArgs.SESSION_ID),
        )

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        QuestionnaireScreen(
            state = state,
            onAnswer = viewModel::answer,
            onPrevious = viewModel::previousPage,
            onNext = {
                when (viewModel.nextPage()) {
                    QuestionnaireNext.DEVICE -> {
                        dispatchFlowEvent(state.sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted)
                    }
                    QuestionnaireNext.ANALYSIS -> {
                        dispatchFlowEvent(state.sessionId, CollectionFlowEvent.PostQuestionnaireSubmitted)
                    }
                    QuestionnaireNext.STAY -> Unit
                }
            },
        )
    }

    private fun dispatchFlowEvent(sessionId: String, event: CollectionFlowEvent) {
        val transition = runCatching {
            ARouter.getInstance().navigation(CollectionFlowProvider::class.java)?.dispatch(sessionId, event)
        }.getOrNull()
        when (transition) {
            is CollectionFlowTransition.Applied,
            is CollectionFlowTransition.AlreadyApplied -> Navigator.flow(
                requireActivity() as FlowNavigationHost,
                sessionId,
                requireNotNull(transition.nextDestination),
            )
            else -> viewModel.reportFlowError()
        }
    }
}
