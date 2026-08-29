package com.smarthealth.vitalhub.feature.collection

import androidx.compose.runtime.Composable
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProvider
import com.smarthealth.vitalhub.feature.collection.shared.connectedDevice
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition

@Route(path = Routes.COLLECTION)
class CollectionFragment : BaseFlowFragment(), AppBarDestination, FlowDestinationOwner {
    private val viewModel by viewModels<CollectionViewModel>()
    private val collectionBluetoothProvider by activityViewModels<CollectionBluetoothProvider>()
    override val appBarTitle: String
        get() = when (arguments?.getString(RouteArgs.COLLECTION_MODE)) {
            CollectionMode.CLIP -> "片段采集中"
            CollectionMode.CONTINUOUS -> "连续记录"
            else -> "实时预览"
        }
    override val flowDestinationContext: FlowDestinationContext
        get() = FlowDestinationContext(
            destination = when (arguments?.getString(RouteArgs.COLLECTION_MODE)) {
                CollectionMode.CLIP -> FlowDestination.CLIP_COLLECTION
                CollectionMode.CONTINUOUS -> FlowDestination.CONTINUOUS_RECORDING
                else -> FlowDestination.LIVE_PREVIEW
            },
            sessionId = arguments?.getString(RouteArgs.SESSION_ID),
        )

    @Composable
    override fun ScreenContent() {
        val bluetoothState = collectionBluetoothProvider.uiState.collectAsStateWithLifecycle().value
        val connectedDevice = bluetoothState.connectedDevice()
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        val displayedDevice = connectedDevice ?: bluetoothState.lastConnectedDevice
        val host = requireActivity() as FlowNavigationHost
        CollectionScreen(
            state = state,
            device = displayedDevice,
            isDeviceConnected = connectedDevice != null,
            onStartClip = {
                Navigator.collection(host, state.sessionId, FlowDestination.CLIP_COLLECTION)
            },
            onStartContinuous = {
                Navigator.collection(host, state.sessionId, FlowDestination.CONTINUOUS_RECORDING)
            },
            onStopClip = { finishCollection(state.sessionId) },
            onFinishContinuous = { finishCollection(state.sessionId) },
        )
    }

    private fun finishCollection(sessionId: String) {
        val transition = runCatching {
            ARouter.getInstance().navigation(CollectionFlowProvider::class.java)
                ?.dispatch(sessionId, CollectionFlowEvent.CollectionCompleted)
        }.getOrNull()
        when (transition) {
            is CollectionFlowTransition.Applied,
            is CollectionFlowTransition.AlreadyApplied -> Navigator.flow(
                requireContext(),
                sessionId,
                requireNotNull(transition.nextDestination),
            )
            else -> viewModel.reportFlowError()
        }
    }
}
