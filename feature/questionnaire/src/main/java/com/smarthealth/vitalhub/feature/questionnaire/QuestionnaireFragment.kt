package com.smarthealth.vitalhub.feature.questionnaire

import android.os.Bundle
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

@Route(path = Routes.QUESTIONNAIRE_FRAGMENT)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        QuestionnaireAnswerStores.initialize(requireContext())
        super.onCreate(savedInstanceState)
    }

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
                        if (viewModel.markSubmitted()) {
                            dispatchFlowEvent(state.sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted)
                        }
                    }
                    QuestionnaireNext.ANALYSIS -> {
                        if (viewModel.markSubmitted()) {
                            dispatchFlowEvent(state.sessionId, CollectionFlowEvent.PostQuestionnaireSubmitted)
                        }
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
        if (arguments?.getString(RouteArgs.FLOW_ENTRY_MODE) == FlowEntryMode.DIRECT_RETURN_HOME) {
            Navigator.returnHome(requireContext())
            return
        }
        when (transition) {
            is CollectionFlowTransition.Applied,
            is CollectionFlowTransition.AlreadyApplied -> {
                val destination = requireNotNull(transition.nextDestination)
                if (destination == FlowDestination.HOME) {
                    Navigator.returnHome(requireContext())
                } else {
                    Navigator.flow(requireContext(), sessionId, destination)
                }
            }
            else -> viewModel.reportFlowError()
        }
    }
}
