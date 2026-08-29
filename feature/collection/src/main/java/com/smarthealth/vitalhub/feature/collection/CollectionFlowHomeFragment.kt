package com.smarthealth.vitalhub.feature.collection

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition

@Route(path = Routes.COLLECTION_FLOW_HOME)
class CollectionFlowHomeFragment : BaseFlowFragment(), AppBarDestination, FlowDestinationOwner {
    private val viewModel by viewModels<CollectionFlowHomeViewModel>()
    private val collectionBluetoothProvider by activityViewModels<CollectionBluetoothProvider>()
    override val appBarTitle = "连接记录仪"
    override val flowDestinationContext: FlowDestinationContext
        get() = FlowDestinationContext(FlowDestination.DEVICE_CONNECTION, arguments?.getString(RouteArgs.SESSION_ID))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectionBluetoothProvider.startScan()
    }

    override fun onStop() {
        collectionBluetoothProvider.stopScan()
        super.onStop()
    }

    @Composable
    override fun ScreenContent() {
        val bluetoothState = collectionBluetoothProvider.uiState.collectAsStateWithLifecycle().value
        val state = viewModel.uiState.collectAsStateWithLifecycle().value.withBluetoothState(bluetoothState)
        LaunchedEffect(collectionBluetoothProvider) {
            collectionBluetoothProvider.connectionSucceeded.collect { continueDeviceConnection() }
        }
        CollectionFlowHomeScreen(
            state = state,
            onProjectOnlyChanged = viewModel::setProjectOnly,
            onRefresh = collectionBluetoothProvider::startScan,
            onDeviceSelected = { viewModel.selectAvailableDevice(it, bluetoothState) },
            onConnect = collectionBluetoothProvider::connect,
            onDisconnect = collectionBluetoothProvider::disconnect,
            onContinue = ::continueDeviceConnection,
        )
    }

    private fun continueDeviceConnection() {
        val sessionId = viewModel.uiState.value.sessionId
        val transition = dispatchFlowEvent(sessionId)
        if (transition is CollectionFlowTransition.Applied || transition is CollectionFlowTransition.AlreadyApplied) {
            Navigator.collection(requireActivity() as FlowNavigationHost, sessionId, requireNotNull(transition.nextDestination))
        } else {
            viewModel.reportFlowError()
        }
    }

    private fun dispatchFlowEvent(sessionId: String): CollectionFlowTransition? = runCatching {
        ARouter.getInstance().navigation(CollectionFlowProvider::class.java)
            ?.dispatch(sessionId, CollectionFlowEvent.DeviceConnectionConfirmed)
    }.getOrNull()
}
