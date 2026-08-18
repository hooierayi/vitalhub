package com.smarthealth.vitalhub.feature.device

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navigation.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment

@Route(path = Routes.DEVICE)
class DeviceScanFragment : BaseFlowFragment(), AppBarDestination {
    private val viewModel by viewModels<DeviceScanViewModel>()
    override val appBarTitle = "连接记录仪"

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        DeviceScanScreen(state, viewModel::setProjectOnly, viewModel::refresh) { deviceId ->
            viewModel.connect(deviceId)
            Navigator.collection(requireActivity() as FlowNavigationHost, state.sessionId, CollectionMode.PREVIEW)
        }
    }
}
