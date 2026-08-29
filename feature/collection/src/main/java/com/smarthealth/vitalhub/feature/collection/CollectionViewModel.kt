package com.smarthealth.vitalhub.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VitalMetric(val name: String, val value: String, val unit: String)
data class CollectionUiState(
    val sessionId: String,
    val mode: String,
    val metrics: List<VitalMetric> = listOf(VitalMetric("心率", "72", "bpm"), VitalMetric("呼吸", "16", "次/分"), VitalMetric("皮温", "36.4", "℃")),
    val clipElapsed: String = "01:24",
    val clipProgress: Float = .7f,
    val recordingElapsed: String = "02:36:18",
    val recordId: String = "REC20240521001",
    val startedAt: String = "2024-05-21  09:41:32",
    val flowError: String? = null,
)

class CollectionViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionUiState(
        sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty(),
        mode = savedStateHandle.get<String>(RouteArgs.COLLECTION_MODE) ?: CollectionMode.PREVIEW,
    ))
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    fun reportFlowError() {
        _uiState.value = _uiState.value.copy(flowError = "采集流程暂不可用，请稍后重试")
    }
}
