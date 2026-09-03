package com.smarthealth.vitalhub.feature.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.AnalysisEntryMode
import com.smarthealth.vitalhub.core.navi.FlowEntryMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisNetwork
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisFailureAction
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisProgress
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisRunner
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisTaskState
import com.smarthealth.vitalhub.feature.analysis.data.DefaultAnalysisRepository
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

internal data class AnalysisUiState(
    val sessionId: String,
    val flowEntryMode: String,
    val analysisEntryMode: String = AnalysisEntryMode.FROM_COLLECTION,
    val recordId: String = "",
    val process: AnalysisTaskState = AnalysisTaskState.Uploading(0),
    val collectionCompletedAt: String,
    val deviceAddress: String,
    val collectorName: String,
) {
    val completed: Boolean get() = process is AnalysisTaskState.Completed

    val resultMarkdown: String?
        get() = (process as? AnalysisTaskState.Completed)?.markdown

    /** Upload cannot safely be abandoned before the server returns an analysis id. */
    val canLeavePage: Boolean get() = process !is AnalysisTaskState.Uploading

    /** Once accepted by the server, analysis can continue asynchronously. */
    val canContinueFlow: Boolean
        get() = process is AnalysisTaskState.Waiting || process is AnalysisTaskState.Completed

    val canOpenPostQuestionnaire: Boolean
        get() = analysisEntryMode == AnalysisEntryMode.FROM_COLLECTION && canContinueFlow

    val usesDirectHomeAction: Boolean
        get() = analysisEntryMode == AnalysisEntryMode.FROM_RECORD &&
            process !is AnalysisTaskState.Failed

    val canRetryProcess: Boolean
        get() = when ((process as? AnalysisTaskState.Failed)?.action) {
            AnalysisFailureAction.RETRY_UPLOAD,
            AnalysisFailureAction.RESUME_QUERY,
            AnalysisFailureAction.RESTART_ANALYSIS -> true
            AnalysisFailureAction.NONE,
            AnalysisFailureAction.RECOLLECT_DATA,
            null -> false
        }

    val canRecollectData: Boolean
        get() = (process as? AnalysisTaskState.Failed)?.action ==
            AnalysisFailureAction.RECOLLECT_DATA
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
        userInfoProvider: UserInfoProvider? = null,
    ) : this(
        savedStateHandle,
        AnalysisViewModelDependencies(
            analysisRunner = analysisRunner,
            initializationError = null,
            userInfoProvider = userInfoProvider,
        ),
    )

    private val recordId = savedStateHandle.get<String>(RouteArgs.RECORD_ID).orEmpty()
    private val flowEntryMode = savedStateHandle.get<String>(RouteArgs.FLOW_ENTRY_MODE)
        ?: FlowEntryMode.SEQUENTIAL
    private val analysisEntryMode = savedStateHandle.get<String>(RouteArgs.ANALYSIS_ENTRY_MODE)
        ?: AnalysisEntryMode.FROM_COLLECTION
    private val analysisRunner = dependencies.analysisRunner
    private val initializationError = dependencies.initializationError
    private val userInfoProvider = dependencies.userInfoProvider
    private var processJob: Job? = null

    private val _uiState = MutableStateFlow(
        AnalysisUiState(
            sessionId = "",
            flowEntryMode = flowEntryMode,
            analysisEntryMode = analysisEntryMode,
            recordId = recordId,
            collectionCompletedAt = "-",
            deviceAddress = "-",
            collectorName = "-",
        ),
    )
    internal val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        startProcess()
    }

    fun retryProcess() {
        val action = (_uiState.value.process as? AnalysisTaskState.Failed)?.action
        if (_uiState.value.canRetryProcess && action != null) {
            startProcess(action)
        }
    }

    private fun startProcess(action: AnalysisFailureAction? = null) {
        processJob?.cancel()
        val runner = analysisRunner
        if (runner == null) {
            _uiState.value = _uiState.value.copy(
                process = AnalysisTaskState.Failed(
                    message = initializationError ?: "分析服务初始化失败",
                    action = AnalysisFailureAction.NONE,
                ),
            )
            return
        }
        processJob = viewModelScope.launch {
            try {
                runner.execute(recordId, action, ::applyProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    process = AnalysisTaskState.Failed(
                        message = error.message ?: "上传或分析失败，请重试",
                        action = AnalysisFailureAction.NONE,
                    ),
                )
            }
        }
    }

    private fun applyProgress(progress: AnalysisProgress) {
        val record = progress.record
        val collectorName = record?.let {
            runCatching { userInfoProvider?.getUser(it.userFingerprint)?.name }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
        }
        _uiState.value = _uiState.value.copy(
            recordId = progress.recordId,
            process = progress.state,
            sessionId = record?.sessionId ?: _uiState.value.sessionId,
            collectionCompletedAt = record?.recordedAtEpochMillis
                ?.let(::formatCollectionTime)
                ?: _uiState.value.collectionCompletedAt,
            deviceAddress = record?.deviceAddress
                ?.takeIf(String::isNotBlank)
                ?: _uiState.value.deviceAddress,
            collectorName = collectorName ?: _uiState.value.collectorName,
        )
    }
}

private data class AnalysisViewModelDependencies(
    val analysisRunner: AnalysisRunner?,
    val initializationError: String?,
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
        userInfoProvider = resolveProvider(),
    )
}

private inline fun <reified T> resolveProvider(): T? = runCatching {
    ARouter.getInstance().navigation(T::class.java)
}.getOrNull()

private fun formatCollectionTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
