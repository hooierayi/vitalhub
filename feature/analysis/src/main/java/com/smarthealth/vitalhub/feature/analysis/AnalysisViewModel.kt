package com.smarthealth.vitalhub.feature.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.FlowEntryMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisNetwork
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisProgress
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisRunner
import com.smarthealth.vitalhub.feature.analysis.data.DefaultAnalysisRepository
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import com.smarthealth.vitalhub.provider.record.AnalysisStatus
import com.smarthealth.vitalhub.provider.record.RecordProvider
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AnalysisProcessStage {
    UPLOADING,
    QUEUED,
    ANALYZING,
    RETRYING,
    COMPLETED,
    FAILED,
}

data class AnalysisUiState(
    val sessionId: String,
    val flowEntryMode: String,
    val recordId: String = "",
    val processStage: AnalysisProcessStage = AnalysisProcessStage.UPLOADING,
    val uploadProgress: Int = 0,
    val processError: String? = null,
    val resultMarkdown: String? = null,
    val collectionCompletedAt: String,
    val deviceAddress: String,
    val collectorName: String,
) {
    val completed: Boolean get() = processStage == AnalysisProcessStage.COMPLETED

    /** Upload cannot safely be abandoned before the server returns an analysis id. */
    val canLeavePage: Boolean get() = processStage != AnalysisProcessStage.UPLOADING

    /** Once accepted by the server, analysis can continue asynchronously. */
    val canContinueFlow: Boolean
        get() = processStage != AnalysisProcessStage.UPLOADING &&
            processStage != AnalysisProcessStage.FAILED
}

class AnalysisViewModel private constructor(
    savedStateHandle: SavedStateHandle,
    dependencies: AnalysisViewModelDependencies,
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle,
        defaultAnalysisDependencies(),
    )

    internal constructor(
        savedStateHandle: SavedStateHandle,
        analysisRunner: AnalysisRunner,
        collectionFlowProvider: CollectionFlowProvider? = null,
        deviceProvider: DeviceProvider? = null,
        userInfoProvider: UserInfoProvider? = null,
    ) : this(
        savedStateHandle,
        AnalysisViewModelDependencies(
            analysisRunner = analysisRunner,
            initializationError = null,
            collectionFlowProvider = collectionFlowProvider,
            deviceProvider = deviceProvider,
            userInfoProvider = userInfoProvider,
        ),
    )

    private val sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty()
    private val flowEntryMode = savedStateHandle.get<String>(RouteArgs.FLOW_ENTRY_MODE)
        ?: FlowEntryMode.SEQUENTIAL
    private val analysisRunner = dependencies.analysisRunner
    private val initializationError = dependencies.initializationError
    private var processJob: Job? = null
    private val collectionCompletedAt = runCatching {
        dependencies.collectionFlowProvider?.getCurrentSession()
            ?.takeIf { snapshot -> snapshot.sessionId == sessionId }
            ?.collectionCompletedAtEpochMillis
    }.getOrNull()

    private val _uiState = MutableStateFlow(
        AnalysisUiState(
            sessionId = sessionId,
            flowEntryMode = flowEntryMode,
            collectionCompletedAt = collectionCompletedAt?.let(::formatCollectionTime) ?: "-",
            deviceAddress = resolveDeviceAddress(dependencies.deviceProvider),
            collectorName = runCatching { dependencies.userInfoProvider?.getUser()?.name }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: "-",
        ),
    )
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        startProcess(retryFailed = false)
    }

    fun retryProcess() {
        if (_uiState.value.processStage == AnalysisProcessStage.FAILED) {
            startProcess(retryFailed = true)
        }
    }

    private fun startProcess(retryFailed: Boolean) {
        processJob?.cancel()
        val runner = analysisRunner
        if (runner == null) {
            _uiState.value = _uiState.value.copy(
                processStage = AnalysisProcessStage.FAILED,
                processError = initializationError ?: "分析服务初始化失败",
            )
            return
        }
        processJob = viewModelScope.launch {
            try {
                runner.execute(sessionId, retryFailed, ::applyProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    processStage = AnalysisProcessStage.FAILED,
                    processError = error.message ?: "上传或分析失败，请重试",
                )
            }
        }
    }

    private fun applyProgress(progress: AnalysisProgress) {
        _uiState.value = _uiState.value.copy(
            recordId = progress.recordId,
            processStage = progress.status.toProcessStage(),
            uploadProgress = progress.uploadProgress,
            processError = progress.errorMessage,
            resultMarkdown = progress.resultMarkdown,
        )
    }
}

private data class AnalysisViewModelDependencies(
    val analysisRunner: AnalysisRunner?,
    val initializationError: String?,
    val collectionFlowProvider: CollectionFlowProvider?,
    val deviceProvider: DeviceProvider?,
    val userInfoProvider: UserInfoProvider?,
)

private fun defaultAnalysisDependencies(): AnalysisViewModelDependencies {
    val recordProvider = resolveProvider<RecordProvider>()
    val runner = runCatching {
        checkNotNull(recordProvider) { "采集记录服务不可用" }
        DefaultAnalysisRepository(
            recordProvider = recordProvider,
            remoteDataSource = AnalysisNetwork.remoteDataSource,
            appVersion = BuildConfig.ANALYSIS_APP_VERSION,
        )
    }
    return AnalysisViewModelDependencies(
        analysisRunner = runner.getOrNull(),
        initializationError = runner.exceptionOrNull()?.message,
        collectionFlowProvider = resolveProvider(),
        deviceProvider = resolveProvider(),
        userInfoProvider = resolveProvider(),
    )
}

private fun AnalysisStatus.toProcessStage(): AnalysisProcessStage = when (this) {
    AnalysisStatus.UPLOADING -> AnalysisProcessStage.UPLOADING
    AnalysisStatus.QUEUED -> AnalysisProcessStage.QUEUED
    AnalysisStatus.PROCESSING -> AnalysisProcessStage.ANALYZING
    AnalysisStatus.RETRYING -> AnalysisProcessStage.RETRYING
    AnalysisStatus.COMPLETED -> AnalysisProcessStage.COMPLETED
    AnalysisStatus.FAILED -> AnalysisProcessStage.FAILED
}

private inline fun <reified T> resolveProvider(): T? = runCatching {
    ARouter.getInstance().navigation(T::class.java)
}.getOrNull()

internal fun resolveDeviceAddress(provider: DeviceProvider?): String = runCatching {
    provider?.getDeviceInfo()?.address?.takeIf(String::isNotBlank)
}.getOrNull() ?: "-"

private fun formatCollectionTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
