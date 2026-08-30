package com.smarthealth.vitalhub.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VitalMetric(val name: String, val value: String, val unit: String)
data class CollectionUiState(
    val sessionId: String,
    val mode: String,
    val metrics: List<VitalMetric> = listOf(VitalMetric("心率", "72", "bpm"), VitalMetric("呼吸", "16", "次/分"), VitalMetric("皮温", "36.4", "℃")),
    val clipDurationSeconds: Long = CollectionConfig.CLIP_DURATION_SECONDS,
    val clipElapsed: String = "00:00",
    val clipRemaining: String = formatClipClock(CollectionConfig.CLIP_DURATION_SECONDS),
    val clipProgress: Float = 0f,
    val isClipCollecting: Boolean = true,
    val clipCountdownFinished: Boolean = false,
    val recordingElapsed: String = "02:36:18",
    val recordId: String = "REC20240521001",
    val startedAt: String = "2024-05-21  09:41:32",
    val flowError: String? = null,
    val deviceDebugInfo: String? = null,
)

class CollectionViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionUiState(
        sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty(),
        mode = savedStateHandle.get<String>(RouteArgs.COLLECTION_MODE) ?: CollectionMode.PREVIEW,
    ))
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()
    private var clipTimerJob: Job? = null

    init {
        if (_uiState.value.mode == CollectionMode.CLIP) {
            val isCollecting = savedStateHandle.get<Boolean>(CLIP_IS_COLLECTING_KEY) ?: true
            if (isCollecting) {
                val startedAt = savedStateHandle.get<Long>(CLIP_STARTED_AT_KEY)
                    ?: System.currentTimeMillis().also { savedStateHandle[CLIP_STARTED_AT_KEY] = it }
                startClipTimer(startedAt)
            } else {
                updateClipTimer(
                    elapsedMillis = savedStateHandle.get<Long>(CLIP_ELAPSED_MILLIS_KEY)
                        ?: savedStateHandle.get<Long>(LEGACY_CLIP_ELAPSED_SECONDS_KEY)?.times(1_000L)
                        ?: 0L,
                    isCollecting = false,
                    countdownFinished = savedStateHandle.get<Boolean>(CLIP_COMPLETION_PENDING_KEY) == true,
                )
            }
        }
    }

    fun stopClipTimer() {
        if (!_uiState.value.isClipCollecting) return
        val elapsedMillis = elapsedMillisSinceStart()
        savedStateHandle[CLIP_IS_COLLECTING_KEY] = false
        savedStateHandle[CLIP_ELAPSED_MILLIS_KEY] = elapsedMillis
        savedStateHandle[CLIP_COMPLETION_PENDING_KEY] = false
        clipTimerJob?.cancel()
        updateClipTimer(elapsedMillis, isCollecting = false)
    }

    fun restartClipTimer() {
        val startedAt = System.currentTimeMillis()
        savedStateHandle[CLIP_STARTED_AT_KEY] = startedAt
        savedStateHandle[CLIP_ELAPSED_MILLIS_KEY] = 0L
        savedStateHandle[CLIP_IS_COLLECTING_KEY] = true
        savedStateHandle[CLIP_COMPLETION_PENDING_KEY] = false
        updateClipTimer(0L, isCollecting = true)
        startClipTimer(startedAt)
    }

    fun consumeClipCountdownFinished() {
        savedStateHandle[CLIP_COMPLETION_PENDING_KEY] = false
        _uiState.value = _uiState.value.copy(clipCountdownFinished = false)
    }

    fun reportFlowError() {
        _uiState.value = _uiState.value.copy(flowError = "采集流程暂不可用，请稍后重试")
    }

    fun reportDeviceError(message: String) {
        _uiState.value = _uiState.value.copy(flowError = message)
    }

    private fun startClipTimer(startedAt: Long) {
        clipTimerJob?.cancel()
        clipTimerJob = viewModelScope.launch {
            val clipDurationMillis = _uiState.value.clipDurationSeconds * 1_000L
            do {
                val elapsedMillis = (System.currentTimeMillis() - startedAt)
                    .coerceIn(0L, clipDurationMillis)
                val remainingMillis = clipDurationMillis - elapsedMillis
                val finished = remainingMillis == 0L
                if (finished) {
                    savedStateHandle[CLIP_IS_COLLECTING_KEY] = false
                    savedStateHandle[CLIP_ELAPSED_MILLIS_KEY] = elapsedMillis
                    savedStateHandle[CLIP_COMPLETION_PENDING_KEY] = true
                }
                updateClipTimer(
                    elapsedMillis = elapsedMillis,
                    isCollecting = !finished,
                    countdownFinished = finished,
                )
                if (remainingMillis > 0L) delay(PROGRESS_UPDATE_INTERVAL_MILLIS)
            } while (remainingMillis > 0L)
        }
    }

    private fun elapsedMillisSinceStart(): Long {
        val startedAt = savedStateHandle.get<Long>(CLIP_STARTED_AT_KEY) ?: return 0L
        return (System.currentTimeMillis() - startedAt)
            .coerceIn(0L, _uiState.value.clipDurationSeconds * 1_000L)
    }

    private fun updateClipTimer(
        elapsedMillis: Long,
        isCollecting: Boolean,
        countdownFinished: Boolean = false,
    ) {
        val clipDurationMillis = _uiState.value.clipDurationSeconds * 1_000L
        val safeElapsedMillis = elapsedMillis.coerceIn(0L, clipDurationMillis)
        val remainingMillis = clipDurationMillis - safeElapsedMillis
        val elapsedSeconds = safeElapsedMillis / 1_000L
        val remainingSeconds = (remainingMillis + 999L) / 1_000L
        _uiState.value = _uiState.value.copy(
            clipElapsed = formatClipClock(elapsedSeconds),
            clipRemaining = formatClipClock(remainingSeconds),
            clipProgress = safeElapsedMillis.toFloat() / clipDurationMillis,
            isClipCollecting = isCollecting,
            clipCountdownFinished = countdownFinished,
        )
    }

    private companion object {
        const val CLIP_STARTED_AT_KEY = "clipStartedAt"
        const val CLIP_ELAPSED_MILLIS_KEY = "clipElapsedMillis"
        const val LEGACY_CLIP_ELAPSED_SECONDS_KEY = "clipElapsedSeconds"
        const val CLIP_IS_COLLECTING_KEY = "clipIsCollecting"
        const val CLIP_COMPLETION_PENDING_KEY = "clipCompletionPending"
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 100L
    }
}
