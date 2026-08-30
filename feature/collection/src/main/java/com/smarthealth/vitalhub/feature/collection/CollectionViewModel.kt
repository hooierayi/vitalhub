package com.smarthealth.vitalhub.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.provider.device.DeviceRecordInfo
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
    val isClipCollecting: Boolean = false,
    val clipCountdownFinished: Boolean = false,
    val isContinuousRecording: Boolean = false,
    val isContinuousStartLoading: Boolean = false,
    val recordingElapsed: String = "00:00:00",
    val recordId: String = "REC-000000000000",
    val startedAt: String = "2024-05-21  09:41:32",
    val flowError: String? = null,
    val deviceDebugInfo: String? = null,
)

class CollectionViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val mode = savedStateHandle.get<String>(RouteArgs.COLLECTION_MODE) ?: CollectionMode.PREVIEW
    private val recordId = savedStateHandle.get<String>(RECORD_ID_KEY)
        ?: "${recordIdPrefix(mode)}${UUID.randomUUID().toString().replace("-", "").take(12)}"
            .also { savedStateHandle[RECORD_ID_KEY] = it }
    private val isContinuousRecording = mode == CollectionMode.CONTINUOUS &&
        savedStateHandle.get<Boolean>(CONTINUOUS_IS_RECORDING_KEY) == true
    private val continuousStartedAt = if (isContinuousRecording) {
        savedStateHandle.get<Long>(RECORD_STARTED_AT_KEY)
            ?: System.currentTimeMillis().also { savedStateHandle[RECORD_STARTED_AT_KEY] = it }
    } else {
        0L
    }
    private val _uiState = MutableStateFlow(CollectionUiState(
        sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty(),
        mode = mode,
        recordId = recordId,
        isContinuousRecording = isContinuousRecording,
        recordingElapsed = continuousStartedAt.takeIf { it > 0L }
            ?.let { formatContinuousElapsed(System.currentTimeMillis() - it) }
            ?: "00:00:00",
        startedAt = continuousStartedAt.takeIf { it > 0L }?.let(::formatRecordTime).orEmpty(),
    ))
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()
    private var clipTimerJob: Job? = null
    private var continuousTimerJob: Job? = null

    init {
        if (_uiState.value.mode == CollectionMode.CLIP) {
            val isCollecting = savedStateHandle.get<Boolean>(CLIP_IS_COLLECTING_KEY) ?: false
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
        } else if (_uiState.value.isContinuousRecording) {
            val timerAnchor = System.currentTimeMillis()
            startContinuousTimer(
                initialElapsedMillis = continuousElapsedAtEntry(
                    enteredAtEpochMillis = timerAnchor,
                    recordStartedAtEpochMillis = continuousStartedAt,
                ),
                timerAnchorEpochMillis = timerAnchor,
            )
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

    fun markLocalRecordingStarted(path: String) {
        val startedAt = System.currentTimeMillis()
        savedStateHandle[RECORD_FILE_PATH_KEY] = path
        savedStateHandle[RECORD_STARTED_AT_KEY] = startedAt
        _uiState.value = _uiState.value.copy(startedAt = formatRecordTime(startedAt))
    }

    fun markContinuousRecordingStarted(): Long {
        val startedAt = System.currentTimeMillis()
        savedStateHandle[RECORD_STARTED_AT_KEY] = startedAt
        savedStateHandle[CONTINUOUS_IS_RECORDING_KEY] = true
        _uiState.value = _uiState.value.copy(
            isContinuousRecording = true,
            recordingElapsed = "00:00:00",
            startedAt = formatRecordTime(startedAt),
            flowError = null,
        )
        startContinuousTimer(
            initialElapsedMillis = 0L,
            timerAnchorEpochMillis = startedAt,
        )
        return startedAt
    }

    fun beginContinuousStart(): Boolean {
        val state = _uiState.value
        if (state.isContinuousStartLoading) return false
        _uiState.value = state.copy(
            isContinuousStartLoading = true,
            flowError = null,
        )
        return true
    }

    fun finishContinuousStart() {
        _uiState.value = _uiState.value.copy(isContinuousStartLoading = false)
    }

    fun restoreContinuousRecord(
        record: DeviceRecordInfo,
        enteredAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        if (mode != CollectionMode.CONTINUOUS) return
        if (record.id.isBlank() || record.startedAtEpochMillis <= 0L) return
        val initialElapsedMillis = continuousElapsedAtEntry(
            enteredAtEpochMillis = enteredAtEpochMillis,
            recordStartedAtEpochMillis = record.startedAtEpochMillis,
        )
        savedStateHandle[RECORD_ID_KEY] = record.id
        savedStateHandle[RECORD_STARTED_AT_KEY] = record.startedAtEpochMillis
        savedStateHandle[CONTINUOUS_IS_RECORDING_KEY] = true
        _uiState.value = _uiState.value.copy(
            recordId = record.id,
            startedAt = formatRecordTime(record.startedAtEpochMillis),
            recordingElapsed = formatContinuousElapsed(initialElapsedMillis),
            isContinuousRecording = true,
            isContinuousStartLoading = false,
            flowError = null,
        )
        startContinuousTimer(
            initialElapsedMillis = initialElapsedMillis,
            timerAnchorEpochMillis = enteredAtEpochMillis,
        )
    }

    fun completedRecord(
        userFingerprint: String,
        deviceAddress: String,
        recordedAtEpochMillis: Long = System.currentTimeMillis(),
    ): CollectionRecord? {
        val state = _uiState.value
        if (state.mode != CollectionMode.CLIP) return null
        val startedAt = savedStateHandle.get<Long>(RECORD_STARTED_AT_KEY) ?: return null
        return CollectionRecord(
            id = recordId,
            sessionId = state.sessionId,
            type = RecordType.CLIP,
            recordedAtEpochMillis = recordedAtEpochMillis,
            durationMillis = (recordedAtEpochMillis - startedAt).coerceAtLeast(0L),
            localFilePath = savedStateHandle.get(RECORD_FILE_PATH_KEY),
            userFingerprint = userFingerprint,
            deviceAddress = deviceAddress,
        )
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

    fun clearFlowError(expectedMessage: String) {
        if (_uiState.value.flowError == expectedMessage) {
            _uiState.value = _uiState.value.copy(flowError = null)
        }
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

    private fun startContinuousTimer(
        initialElapsedMillis: Long,
        timerAnchorEpochMillis: Long,
    ) {
        continuousTimerJob?.cancel()
        continuousTimerJob = viewModelScope.launch {
            while (true) {
                val elapsedSinceEntry = (System.currentTimeMillis() - timerAnchorEpochMillis)
                    .coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(
                    recordingElapsed = formatContinuousElapsed(
                        initialElapsedMillis + elapsedSinceEntry,
                    ),
                )
                delay(CONTINUOUS_TIMER_INTERVAL_MILLIS)
            }
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
        const val CONTINUOUS_TIMER_INTERVAL_MILLIS = 1_000L
        const val CONTINUOUS_IS_RECORDING_KEY = "continuousIsRecording"
        const val RECORD_ID_KEY = "recordId"
        const val RECORD_STARTED_AT_KEY = "recordStartedAt"
        const val RECORD_FILE_PATH_KEY = "recordFilePath"
    }
}

internal fun recordIdPrefix(mode: String): String = when (mode) {
    CollectionMode.CLIP -> "CLIP-"
    CollectionMode.CONTINUOUS -> "CONT-"
    else -> "REC-"
}

private fun formatRecordTime(epochMillis: Long): String = SimpleDateFormat(
    "yyyy-MM-dd  HH:mm:ss",
    Locale.getDefault(),
).format(Date(epochMillis))

private fun formatContinuousElapsed(elapsedMillis: Long): String {
    val elapsedSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    val hours = elapsedSeconds / 3_600L
    val minutes = (elapsedSeconds % 3_600L) / 60L
    val seconds = elapsedSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

internal fun continuousElapsedAtEntry(
    enteredAtEpochMillis: Long,
    recordStartedAtEpochMillis: Long,
): Long = (enteredAtEpochMillis - recordStartedAtEpochMillis).coerceAtLeast(0L)
