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
import com.smarthealth.vitalhub.foundation.device.waveform.ui.rememberRealtimeWaveformState
import com.smarthealth.vitalhub.feature.collection.shared.CollectionBluetoothProvider
import com.smarthealth.vitalhub.feature.collection.shared.connectedDevice
import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.ContinuousCollectionSubject
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import com.smarthealth.vitalhub.foundation.device.api.FrameContinuity
import com.smarthealth.vitalhub.foundation.file.protocol.AcquisitionDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.DicomRecordingDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.DicomRolloverPolicy
import com.smarthealth.vitalhub.foundation.file.protocol.DicomWorkspace
import com.smarthealth.vitalhub.foundation.file.protocol.DicomWriterPolicy
import com.smarthealth.vitalhub.foundation.file.protocol.EquipmentDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.LocalFileDicomPublisher
import com.smarthealth.vitalhub.foundation.file.protocol.PatientDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.PatientSex
import com.smarthealth.vitalhub.foundation.file.protocol.PersonName
import com.smarthealth.vitalhub.foundation.file.protocol.SeriesDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.StudyDefinition
import com.smarthealth.vitalhub.foundation.file.protocol.UuidDicomUidGenerator
import com.smarthealth.vitalhub.foundation.file.protocol.WearableSignalLayout
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import com.smarthealth.vitalhub.provider.device.DeviceRecordInfo
import com.smarthealth.vitalhub.provider.record.RecordProvider
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import java.io.File
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Route(path = Routes.COLLECTION)
class CollectionFragment : BaseFlowFragment(), AppBarDestination, FlowDestinationOwner {
    private val viewModel by viewModels<CollectionViewModel>()
    private val collectionBluetoothProvider by activityViewModels<CollectionBluetoothProvider>()
    private val dicomUidGenerator = UuidDicomUidGenerator()
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
        val ecgWaveformState = rememberRealtimeWaveformState(sampleRateHz = 250, debugLabel = "ECG")
        val respirationWaveformState = rememberRealtimeWaveformState(sampleRateHz = 250, debugLabel = "呼吸")
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
        LaunchedEffect(state.mode) {
            if (state.mode == CollectionMode.CONTINUOUS) {
                val enteredAtEpochMillis = System.currentTimeMillis()
                runCatching { resolveDeviceProvider()?.getRecordInfo() }
                    .getOrNull()
                    ?.let { record ->
                        viewModel.restoreContinuousRecord(record, enteredAtEpochMillis)
                    }
            }
        }
        LaunchedEffect(state.mode, state.flowError) {
            val error = state.flowError
            if (state.mode == CollectionMode.CONTINUOUS && error != null) {
                delay(CONTINUOUS_ERROR_DISPLAY_MILLIS)
                viewModel.clearFlowError(error)
            }
        }
        LaunchedEffect(state.clipCountdownFinished) {
            if (state.clipCountdownFinished) {
                viewModel.consumeClipCountdownFinished()
                completeClipAndOpenAnalysis(
                    recordId = state.recordId,
                    sessionId = state.sessionId,
                )
            }
        }
        val displayState = state.copy(
            metrics = deviceMetrics?.let { metrics ->
                listOf(
                    VitalMetric("帧序号", metrics.sequence.toString(), "seq"),
                    VitalMetric("皮温", metrics.skinCelsius.formatSensorHundredths(), "℃"),
                    VitalMetric("湿度", metrics.humidityPercent.formatSensorHundredths(), "%"),
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
                openClipPage(host, state.sessionId)
            },
            onStartContinuous = {
                openContinuousPage(host, state.sessionId)
            },
            onPreview = {
                if (
                    state.mode == CollectionMode.CONTINUOUS &&
                    state.isContinuousStartedThisVisit
                ) {
                    startPreviewAfterContinuousRecording(host, state.sessionId)
                } else {
                    returnToPreview(host, state.sessionId, state.isClipCollecting)
                }
            },
            onStopClip = { pauseClipCollection() },
            onRestartClip = { restartClipRecording(state.sessionId) },
            onStartContinuousRecording = { startContinuousRecording() },
            onReturnToDeviceConnection = { disconnectAndReturnToDeviceConnection() },
        )
    }

    private fun openClipPage(host: FlowNavigationHost, sessionId: String) {
        Navigator.collection(
            host,
            sessionId,
            FlowDestination.CLIP_COLLECTION,
            entryMode = flowEntryMode,
        )
    }

    private fun openContinuousPage(host: FlowNavigationHost, sessionId: String) {
        Navigator.collection(
            host,
            sessionId,
            FlowDestination.CONTINUOUS_RECORDING,
            entryMode = flowEntryMode,
        )
    }

    private fun startContinuousRecording() {
        if (!viewModel.beginContinuousStart()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = collectionBluetoothProvider.execute(DeviceCommand.StopCollection)) {
                    CommandResult.Success -> Unit
                    is CommandResult.Rejected -> {
                        viewModel.reportDeviceError(
                            "设备拒绝停止采集，状态码=${result.status}",
                        )
                        return@launch
                    }
                    is CommandResult.Failed -> {
                        viewModel.reportDeviceError(
                            result.cause.message
                                ?.takeIf(String::isNotBlank)
                                ?.let { "停止采集失败：$it" }
                                ?: "停止采集指令失败",
                        )
                        return@launch
                    }
                }
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
                    CommandResult.Success -> {
                        val startedAt = viewModel.markContinuousRecordingStarted()
                        val record = DeviceRecordInfo(
                            id = viewModel.uiState.value.recordId,
                            startedAtEpochMillis = startedAt,
                        )
                        if (!saveDeviceRecord(record)) {
                            viewModel.reportDeviceError("连续记录已启动，但设备写卡记录保存失败")
                        }
                    }
                    is CommandResult.Rejected -> viewModel.reportDeviceError(
                        "设备拒绝启动记录，状态码=${result.status}",
                    )
                    is CommandResult.Failed -> viewModel.reportDeviceError(
                        result.cause.message ?: "启动记录指令失败",
                    )
                }
            } finally {
                viewModel.finishContinuousStart()
            }
        }
    }

    private fun startPreviewAfterContinuousRecording(
        host: FlowNavigationHost,
        sessionId: String,
    ) {
        if (!viewModel.beginContinuousNavigation(isReturnAction = false)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = collectionBluetoothProvider.execute(DeviceCommand.StartCollection)) {
                    CommandResult.Success -> returnToPreview(host, sessionId, isCollecting = false)
                    is CommandResult.Rejected -> viewModel.reportDeviceError(
                        "设备拒绝启动采集，状态码=${result.status}",
                    )
                    is CommandResult.Failed -> viewModel.reportDeviceError(
                        result.cause.message
                            ?.takeIf(String::isNotBlank)
                            ?.let { "启动采集失败：$it" }
                            ?: "启动采集指令失败",
                    )
                }
            } finally {
                viewModel.finishContinuousNavigation()
            }
        }
    }

    private fun disconnectAndReturnToDeviceConnection() {
        if (!viewModel.beginContinuousNavigation(isReturnAction = true)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val disconnected = collectionBluetoothProvider
                    .disconnectKeepingContinuousRecording()
                if (disconnected) {
                    (requireActivity() as CollectionFlowActivity).returnToCollectionHome()
                } else {
                    viewModel.reportDeviceError("蓝牙断开失败，请重试")
                }
            } finally {
                viewModel.finishContinuousNavigation()
            }
        }
    }

    private fun pauseClipCollection() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { stopLocalRecording() }
                .onSuccess { viewModel.stopClipTimer() }
                .onFailure {
                    viewModel.stopClipTimer()
                    viewModel.reportDeviceError(it.message ?: "停止本地记录失败")
                }
        }
    }

    private fun returnToPreview(
        host: FlowNavigationHost,
        sessionId: String,
        isCollecting: Boolean,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (isCollecting) {
                runCatching { stopLocalRecording() }
                    .onFailure { viewModel.reportDeviceError(it.message ?: "停止本地记录失败") }
                viewModel.stopClipTimer()
            }
            if (parentFragmentManager.backStackEntryCount > 0) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } else {
                Navigator.collection(
                    host,
                    sessionId,
                    FlowDestination.LIVE_PREVIEW,
                    addToBackStack = false,
                    entryMode = flowEntryMode,
                )
            }
        }
    }

    private fun restartClipRecording(sessionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val directory = requireContext().getExternalFilesDir("records")
                ?: File(requireContext().filesDir, "records")
            val user = runCatching {
                ARouter.getInstance().navigation(UserInfoProvider::class.java)?.getUser()
            }.getOrNull()
            val device = resolveDeviceProvider()?.getDeviceInfo()
            if (user == null || device == null) {
                viewModel.reportDeviceError("缺少用户或设备信息，无法创建DICOM记录")
                return@launch
            }
            val startedAt = System.currentTimeMillis()
            val prefix = "${viewModel.uiState.value.recordId}-$startedAt"
            val target = File(directory, "$prefix-001.dcm")
            val definition = DicomRecordingDefinition(
                sessionId = sessionId,
                patient = PatientDefinition(
                    patientName = PersonName(familyName = user.name, givenName = null),
                    patientId = user.fingerprint,
                    issuerOfPatientId = "VITALHUB",
                    birthDate = null,
                    ageYears = user.age,
                    sex = when (user.gender) {
                        Gender.MALE -> PatientSex.MALE
                        Gender.FEMALE -> PatientSex.FEMALE
                        Gender.UNSPECIFIED -> null
                    },
                ),
                study = StudyDefinition(
                    studyInstanceUid = viewModel.getOrCreateStudyInstanceUid(
                        dicomUidGenerator::newStudyInstanceUid,
                    ),
                    studyId = viewModel.uiState.value.recordId,
                    studyDateTimeEpochMillis = startedAt,
                    accessionNumber = null,
                ),
                series = SeriesDefinition(
                    seriesInstanceUid = dicomUidGenerator.newSeriesInstanceUid(),
                    seriesNumber = 1,
                ),
                equipment = EquipmentDefinition(
                    manufacturer = device.name.orEmpty(),
                    modelName = device.address,
                    serialNumber = null,
                    softwareVersions = listOf("VitalHub Android/${BuildConfig.COLLECTION_APP_VERSION}"),
                ),
                acquisition = AcquisitionDefinition(
                    startedAtEpochMillis = startedAt,
                    timezoneOffset = SimpleDateFormat("Z", Locale.US).format(Date(startedAt)),
                ),
                signalLayout = WearableSignalLayout(),
                writerPolicy = DicomWriterPolicy(
                    rollover = DicomRolloverPolicy(),
                ),
            )
            runCatching {
                collectionBluetoothProvider.startRecording(
                    definition = definition,
                    workspace = DicomWorkspace(File(directory, "$prefix.work")),
                    publisher = LocalFileDicomPublisher(directory, prefix),
                    maximumFrameCount = viewModel.uiState.value.clipDurationSeconds,
                )
            }
                .onSuccess {
                    viewModel.markLocalRecordingStarted(target.absolutePath)
                    viewModel.restartClipTimer()
                }
                .onFailure {
                    viewModel.reportDeviceError(it.message ?: "无法创建采集文件")
                }
        }
    }

    private fun completeClipAndOpenAnalysis(recordId: String, sessionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val finalized = runCatching { stopLocalRecording() }
                .onFailure { viewModel.reportDeviceError(it.message ?: "DICOM文件生成失败") }
                .getOrDefault(false)
            if (!finalized) {
                viewModel.reportDeviceError("采集已结束，但DICOM文件生成失败，请重新采集")
                return@launch
            }
            if (!saveCompletedRecord()) {
                viewModel.reportDeviceError("采集已结束，但记录保存失败，请重新采集")
                return@launch
            }
            runCatching {
                ARouter.getInstance().navigation(CollectionFlowProvider::class.java)
                    ?.dispatch(sessionId, CollectionFlowEvent.CollectionCompleted)
            }
            Navigator.analysis(
                context = requireContext(),
                recordId = recordId,
                entryMode = flowEntryMode,
                analysisEntryMode = AnalysisEntryMode.FROM_COLLECTION,
                finishSourceOnArrival = true,
            )

            // The upload-page transition must not wait for a device receipt. Stop the device
            // only as best-effort cleanup after navigation has already been requested.
            runCatching { collectionBluetoothProvider.execute(DeviceCommand.StopCollection) }
        }
    }

    private suspend fun saveCompletedRecord(): Boolean {
        val user = runCatching {
            ARouter.getInstance().navigation(UserInfoProvider::class.java)?.getUser()
        }.getOrNull()
        val deviceAddress = resolveDeviceProvider()?.getDeviceInfo()?.address
        val record = viewModel.completedRecord(
            userFingerprint = user?.fingerprint ?: return false,
            deviceAddress = deviceAddress ?: return false,
        ) ?: return false
        return runCatching {
            ARouter.getInstance().navigation(RecordProvider::class.java)?.saveRecord(record) == true
        }.getOrDefault(false)
    }

    private suspend fun stopLocalRecording(): Boolean {
        val location = collectionBluetoothProvider.stopRecording()
            ?.segments
            ?.firstOrNull()
            ?.publishedFile
            ?.location
            ?: return false
        viewModel.markLocalRecordingFinished(location)
        return true
    }

    private fun saveDeviceRecord(record: DeviceRecordInfo): Boolean {
        val provider = resolveDeviceProvider() ?: return false
        val deviceInfo = provider.getDeviceInfo() ?: return false
        return provider.saveDevice(deviceInfo.copy(record = record))
    }

    private fun resolveDeviceProvider(): DeviceProvider? = runCatching {
        ARouter.getInstance().navigation(DeviceProvider::class.java)
    }.getOrNull()

    private companion object {
        const val CONTINUOUS_ERROR_DISPLAY_MILLIS = 3_000L
    }
}
