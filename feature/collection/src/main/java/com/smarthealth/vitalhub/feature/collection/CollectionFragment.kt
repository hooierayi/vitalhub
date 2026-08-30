package com.smarthealth.vitalhub.feature.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.*
import com.smarthealth.vitalhub.core.ui.BaseFlowFragment
import com.smarthealth.vitalhub.core.waveform.rememberRealtimeWaveformState
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProvider
import com.smarthealth.vitalhub.feature.collection.shared.connectedDevice
import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.ContinuousCollectionSubject
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.api.FrameContinuity
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.launch

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

    private val flowEntryMode: String
        get() = arguments?.getString(RouteArgs.FLOW_ENTRY_MODE) ?: FlowEntryMode.SEQUENTIAL

    @Composable
    override fun ScreenContent() {
        val bluetoothState = collectionBluetoothProvider.uiState.collectAsStateWithLifecycle().value
        val connectedDevice = bluetoothState.connectedDevice()
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        val deviceMetrics = collectionBluetoothProvider.metrics.collectAsStateWithLifecycle().value
        val latestFrame = collectionBluetoothProvider.frames
            .collectAsStateWithLifecycle(initialValue = null)
            .value
        val ecgWaveformState = rememberRealtimeWaveformState(sampleRateHz = 250)
        val respirationWaveformState = rememberRealtimeWaveformState(sampleRateHz = 250)
        LaunchedEffect(collectionBluetoothProvider) {
            collectionBluetoothProvider.ecgWaveforms.collect { frame ->
                if (frame.continuity != FrameContinuity.DUPLICATE) {
                    ecgWaveformState.append(frame.samples)
                }
            }
        }
        LaunchedEffect(collectionBluetoothProvider) {
            collectionBluetoothProvider.respirationWaveforms.collect { frame ->
                if (frame.continuity != FrameContinuity.DUPLICATE) {
                    respirationWaveformState.append(frame.samples)
                }
            }
        }
        val diagnostic = collectionBluetoothProvider.diagnostics
            .collectAsStateWithLifecycle(initialValue = null)
            .value
        val displayedDevice = connectedDevice ?: bluetoothState.lastConnectedDevice
        val host = requireActivity() as FlowNavigationHost
        val displayState = state.copy(
            metrics = deviceMetrics?.let { metrics ->
                listOf(
                    VitalMetric("帧序号", metrics.sequence.toString(), "seq"),
                    VitalMetric("皮温", "%.2f".format(metrics.skinCelsius), "℃"),
                    VitalMetric("湿度", "%.2f".format(metrics.humidityPercent), "%"),
                )
            } ?: state.metrics,
            deviceDebugInfo = when {
                bluetoothState.connectionError != null -> bluetoothState.connectionError
                connectedDevice != null && deviceMetrics == null -> diagnostic
                    ?: "SDK 已连接，等待记录仪数据帧"
                deviceMetrics != null -> "SDK 收帧正常 · sequence=${deviceMetrics.sequence}"
                else -> null
            },
        )
        CollectionScreen(
            state = displayState,
            device = displayedDevice,
            isDeviceConnected = connectedDevice != null,
            ecgWaveformState = ecgWaveformState,
            respirationWaveformState = respirationWaveformState,
            latestFrame = latestFrame,
            onStartClip = {
                startClipRecording(host, state.sessionId)
            },
            onStartContinuous = {
                startContinuous(host, state.sessionId)
            },
            onStopClip = { stopCollection(state.sessionId) },
            onFinishContinuous = { stopCollection(state.sessionId) },
        )
    }

    private fun startClipRecording(host: FlowNavigationHost, sessionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val directory = requireContext().getExternalFilesDir("records")
                ?: File(requireContext().filesDir, "records")
            val target = File(directory, "$sessionId-${System.currentTimeMillis()}.vhf")
            runCatching { collectionBluetoothProvider.startRecording(target.absolutePath) }
                .onSuccess {
                    Navigator.collection(
                        host,
                        sessionId,
                        FlowDestination.CLIP_COLLECTION,
                        entryMode = flowEntryMode,
                    )
                }
                .onFailure { viewModel.reportDeviceError(it.message ?: "无法创建采集文件") }
        }
    }

    private fun startContinuous(host: FlowNavigationHost, sessionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = runCatching {
                ARouter.getInstance().navigation(UserInfoProvider::class.java)?.getUser()
            }.getOrNull()
            if (user == null) {
                viewModel.reportDeviceError("未读取到完整用户资料")
                return@launch
            }
            val now = Calendar.getInstance()
            val command = DeviceCommand.StartContinuous(
                ContinuousCollectionSubject(
                    name = user.name,
                    genderCode = if (user.gender == Gender.MALE) 0x01 else 0x02,
                    age = user.age,
                    year = now.get(Calendar.YEAR),
                    month = now.get(Calendar.MONTH) + 1,
                    day = now.get(Calendar.DAY_OF_MONTH),
                    hour = now.get(Calendar.HOUR_OF_DAY),
                    minute = now.get(Calendar.MINUTE),
                    second = now.get(Calendar.SECOND),
                ),
            )
            when (val result = collectionBluetoothProvider.execute(command)) {
                CommandResult.Success -> Navigator.collection(
                    host,
                    sessionId,
                    FlowDestination.CONTINUOUS_RECORDING,
                    entryMode = flowEntryMode,
                )
                is CommandResult.Rejected -> viewModel.reportDeviceError(
                    "设备拒绝连续记录，状态码=${result.status}",
                )
                is CommandResult.Failed -> viewModel.reportDeviceError(
                    result.cause.message ?: "连续记录指令失败",
                )
            }
        }
    }

    private fun stopCollection(sessionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = collectionBluetoothProvider.execute(DeviceCommand.StopCollection)
            runCatching { collectionBluetoothProvider.stopRecording() }
            when (result) {
                CommandResult.Success -> finishCollection(sessionId)
                is CommandResult.Rejected -> viewModel.reportDeviceError(
                    "设备拒绝停止采集，状态码=${result.status}",
                )
                is CommandResult.Failed -> viewModel.reportDeviceError(
                    result.cause.message ?: "停止采集指令失败",
                )
            }
        }
    }

    private fun finishCollection(sessionId: String) {
        val transition = runCatching {
            ARouter.getInstance().navigation(CollectionFlowProvider::class.java)
                ?.dispatch(sessionId, CollectionFlowEvent.CollectionCompleted)
        }.getOrNull()
        if (flowEntryMode == FlowEntryMode.DIRECT_RETURN_HOME) {
            Navigator.returnHome(requireContext())
            return
        }
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
