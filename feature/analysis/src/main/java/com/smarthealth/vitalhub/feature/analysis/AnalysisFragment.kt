package com.smarthealth.vitalhub.feature.analysis

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.AppBarDestination
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

@Route(path = Routes.ANALYSIS_FRAGMENT)
class AnalysisFragment : BaseFlowFragment(), AppBarDestination {
    private val viewModel by viewModels<AnalysisViewModel>()
    override val appBarTitle = "AI分析结果"

    @Composable
    override fun ScreenContent() = AnalysisScreen(viewModel.uiState.collectAsStateWithLifecycle().value, onDetails = viewModel::openDetails)
}
