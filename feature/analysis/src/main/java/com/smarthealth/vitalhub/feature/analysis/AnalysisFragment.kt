package com.smarthealth.vitalhub.feature.analysis

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.AppBarDestination
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment
import kotlinx.coroutines.launch

@Route(path = Routes.ANALYSIS_FRAGMENT)
class AnalysisFragment : BaseFlowFragment(), AppBarDestination {
    private val viewModel by viewModels<AnalysisViewModel>()
    override val appBarTitle = "AI分析结果"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val blockBackDuringUpload = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, blockBackDuringUpload)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    blockBackDuringUpload.isEnabled = !state.canLeavePage
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        AnalysisScreen(
            state = state,
            onHome = { Navigator.returnHome(requireContext()) },
            onRetry = viewModel::retryProcess,
            onRecollect = {
                Navigator.flow(
                    context = requireContext(),
                    sessionId = state.sessionId,
                    destination = FlowDestination.CLIP_COLLECTION,
                    entryMode = state.flowEntryMode,
                )
            },
            onPostQuestionnaire = {
                if (state.canContinueFlow) {
                    Navigator.flow(
                        context = requireContext(),
                        sessionId = state.sessionId,
                        destination = FlowDestination.POST_QUESTIONNAIRE,
                        entryMode = state.flowEntryMode,
                    )
                }
            },
        )
    }
}
