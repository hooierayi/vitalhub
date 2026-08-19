package com.smarthealth.vitalhub.feature.device

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

@Route(path = Routes.DEVICE)
class DeviceScanFragment : BaseFlowFragment(), AppBarDestination, FlowDestinationOwner {
    private val viewModel by viewModels<DeviceScanViewModel>()
    override val appBarTitle = "连接记录仪"
    override val flowDestinationContext: FlowDestinationContext
        get() = FlowDestinationContext(FlowDestination.DEVICE_CONNECTION, arguments?.getString(RouteArgs.SESSION_ID))

    @Composable
    override fun ScreenContent() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        DeviceScanScreen(state, viewModel::setProjectOnly, viewModel::refresh) { deviceId ->
            val transition = dispatchFlowEvent(state.sessionId)
            if (transition is CollectionFlowTransition.Applied || transition is CollectionFlowTransition.AlreadyApplied) {
                viewModel.connect(deviceId)
                Navigator.flow(requireActivity() as FlowNavigationHost, state.sessionId, requireNotNull(transition.nextDestination))
            } else {
                viewModel.reportFlowError()
            }
        }
    }

    private fun dispatchFlowEvent(sessionId: String): CollectionFlowTransition? = runCatching {
        ARouter.getInstance().navigation(CollectionFlowProvider::class.java)
            ?.dispatch(sessionId, CollectionFlowEvent.DeviceConnectionConfirmed)
    }.getOrNull()
}
