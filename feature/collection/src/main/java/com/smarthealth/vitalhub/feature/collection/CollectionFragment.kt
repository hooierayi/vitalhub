package com.smarthealth.vitalhub.feature.collection

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navigation.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

@Route(path = Routes.COLLECTION)
class CollectionFragment : BaseFlowFragment(), AppBarDestination {
    private val viewModel by viewModels<CollectionViewModel>()
    override val appBarTitle: String
        get() = when (arguments?.getString(RouteArgs.COLLECTION_MODE)) {
            CollectionMode.CLIP -> "片段采集中"
            CollectionMode.CONTINUOUS -> "连续记录"
            else -> "实时预览"
        }

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        val host = requireActivity() as FlowNavigationHost
        CollectionScreen(
            state,
            onStartClip = { Navigator.collection(host, state.sessionId, CollectionMode.CLIP) },
            onStartContinuous = { Navigator.collection(host, state.sessionId, CollectionMode.CONTINUOUS) },
            onStopClip = { parentFragmentManager.popBackStack() },
            onFinishContinuous = { Navigator.questionnaire(host, state.sessionId, QuestionnairePhase.POST) },
        )
    }
}
