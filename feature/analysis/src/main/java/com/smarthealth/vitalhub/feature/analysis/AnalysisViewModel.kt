package com.smarthealth.vitalhub.feature.analysis

import android.annotation.SuppressLint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalysisMetric(
    val name: String,
    val value: String,
    val unit: String,
    val comparison: String,
    val increasing: Boolean,
    val trend: List<Float>,
)

enum class AnalysisProcessStage {
    UPLOADING,
    ANALYZING,
    COMPLETED,
    FAILED,
}

data class AnalysisUiState(
    val sessionId: String,
    val processStage: AnalysisProcessStage = AnalysisProcessStage.UPLOADING,
    val uploadProgress: Int = 0,
    val processError: String? = null,
    val metrics: List<AnalysisMetric> = listOf(
        AnalysisMetric("心率", "72", "次/分", "较上次下降 4", false, listOf(78f, 76f, 75f, 74f, 72f)),
        AnalysisMetric("血氧饱和度", "98", "%", "较上次上升 1", true, listOf(94f, 95f, 96f, 98f)),
        AnalysisMetric("睡眠时长", "7.2", "小时", "较上次上升 0.6", true, listOf(5.8f, 6.3f, 6.5f, 7.2f)),
    ),
    val collectionCompletedAt: String,
    val deviceName: String,
    val collectorName: String,
) {
    val completed: Boolean get() = processStage == AnalysisProcessStage.COMPLETED
}

class AnalysisViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val collectionFlowProvider = resolveProvider<CollectionFlowProvider>()
    private val deviceProvider = resolveProvider<DeviceProvider>()
    private val userInfoProvider = resolveProvider<UserInfoProvider>()
    private val sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty()
    private val collectionCompletedAt = runCatching {
        collectionFlowProvider?.getCurrentSession()
            ?.takeIf { snapshot -> snapshot.sessionId == sessionId }
            ?.collectionCompletedAtEpochMillis
    }.getOrNull()
    private var mockProcessJob: Job? = null

    private val _uiState = MutableStateFlow(
        AnalysisUiState(
            sessionId = sessionId,
            collectionCompletedAt = collectionCompletedAt?.let(::formatCollectionTime) ?: "-",
            deviceName = resolveDeviceName(deviceProvider),
            collectorName = runCatching { userInfoProvider?.getUser()?.name }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: "-",
        ),
    )
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        startMockProcess()
    }

    fun retryMockProcess() {
        if (_uiState.value.processStage == AnalysisProcessStage.FAILED) startMockProcess()
    }

    private fun startMockProcess() {
        mockProcessJob?.cancel()
        mockProcessJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                processStage = AnalysisProcessStage.UPLOADING,
                uploadProgress = 0,
                processError = null,
            )
            try {
                for (progress in 5..100 step 5) {
                    delay(MOCK_UPLOAD_STEP_DELAY_MILLIS)
                    _uiState.value = _uiState.value.copy(uploadProgress = progress)
                }
                _uiState.value = _uiState.value.copy(processStage = AnalysisProcessStage.ANALYZING)
                delay(MOCK_ANALYSIS_DELAY_MILLIS)
                _uiState.value = _uiState.value.copy(processStage = AnalysisProcessStage.COMPLETED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    processStage = AnalysisProcessStage.FAILED,
                    processError = "上传或分析失败，请重试",
                )
            }
        }
    }

    private companion object {
        const val MOCK_UPLOAD_STEP_DELAY_MILLIS = 80L
        const val MOCK_ANALYSIS_DELAY_MILLIS = 1_500L
    }
}

private inline fun <reified T> resolveProvider(): T? = runCatching {
    ARouter.getInstance().navigation(T::class.java)
}.getOrNull()

@SuppressLint("MissingPermission")
private fun resolveDeviceName(provider: DeviceProvider?): String = runCatching {
    provider?.getCurrentDeviceName()?.takeIf(String::isNotBlank)
        ?: provider?.getCurrentDevice()?.let { device ->
            device.bluetoothDevice?.name?.takeIf(String::isNotBlank)
                ?: device.key.takeIf(String::isNotBlank)
        }
}.getOrNull() ?: "-"

private fun formatCollectionTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
